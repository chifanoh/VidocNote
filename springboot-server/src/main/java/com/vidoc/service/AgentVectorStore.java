package com.vidoc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidoc.config.AgentRagProperties;
import com.vidoc.config.AiEmbeddingProperties;
import com.vidoc.exception.ApiException;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.BooleanOutput;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 8 原生 Vector Set 存储：每个摘要一个带 TTL 的向量集合。
 */
@Service
public class AgentVectorStore {

    private static final Logger log = LoggerFactory.getLogger(AgentVectorStore.class);
    private static final String VECTOR_PREFIX = "vidoc:agent:vector:";
    private static final String STATE_PREFIX = "vidoc:agent:index:";
    private static final ProtocolKeyword VADD = () -> "VADD".getBytes(StandardCharsets.UTF_8);
    private static final ProtocolKeyword VSIM = () -> "VSIM".getBytes(StandardCharsets.UTF_8);
    private static final ByteArrayCodec CODEC = ByteArrayCodec.INSTANCE;

    private final StringRedisTemplate redis;
    private final AgentRagProperties properties;
    private final AiEmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;

    public AgentVectorStore(StringRedisTemplate redis, AgentRagProperties properties,
                            AiEmbeddingProperties embeddingProperties, ObjectMapper objectMapper) {
        this.redis = redis;
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.objectMapper = objectMapper;
    }

    public boolean isReady(String summaryId) {
        try {
            String state = redis.opsForValue().get(stateKey(summaryId));
            return expectedState().equals(state) && Boolean.TRUE.equals(redis.hasKey(vectorKey(summaryId)));
        } catch (DataAccessException e) {
            throw unavailable("无法读取问答向量索引", e);
        }
    }

    public void store(String summaryId, List<TranscriptChunk> chunks, List<float[]> vectors) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("切片数量与向量数量不一致");
        }
        String key = vectorKey(summaryId);
        try {
            redis.delete(List.of(key, stateKey(summaryId)));
            redis.execute((RedisCallback<Void>) connection -> {
                RedisCommands<byte[], byte[]> commands = nativeCommands(connection);
                for (int i = 0; i < chunks.size(); i++) {
                    TranscriptChunk chunk = chunks.get(i);
                    CommandArgs<byte[], byte[]> args = new CommandArgs<>(CODEC)
                            .addKey(bytes(key))
                            .add("FP32")
                            .add(floatVector(vectors.get(i)))
                            .addValue(bytes(Integer.toString(chunk.index())))
                            .add("SETATTR")
                            .addValue(bytes(chunkAttribute(chunk)));
                    commands.dispatch(VADD, new BooleanOutput<>(CODEC), args);
                }
                connection.expire(bytes(key), properties.getVectorTtl().toSeconds());
                return null;
            });
            redis.opsForValue().set(stateKey(summaryId), expectedState(), properties.getVectorTtl());
        } catch (RuntimeException e) {
            try {
                redis.delete(List.of(key, stateKey(summaryId)));
            } catch (DataAccessException ignored) {
                // 原始写入异常更有诊断价值。
            }
            throw unavailable("Redis 向量索引写入失败，请确认 Redis 8.2+ 已启用 Vector Set", e);
        }
    }

    public List<TranscriptChunk> search(String summaryId, float[] queryVector) {
        String key = vectorKey(summaryId);
        try {
            Object result = redis.execute((RedisCallback<Object>) connection -> {
                CommandArgs<byte[], byte[]> args = new CommandArgs<>(CODEC)
                        .addKey(bytes(key))
                        .add("FP32")
                        .add(floatVector(queryVector))
                        .add("WITHATTRIBS")
                        .add("COUNT")
                        .add(Math.max(properties.getTopK(), 1));
                return nativeCommands(connection)
                        .dispatch(VSIM, new NestedMultiOutput<>(CODEC), args);
            });
            return parseSearchResult(result);
        } catch (RuntimeException e) {
            throw unavailable("Redis 向量检索失败，请确认 Redis 8.2+ 已启用 Vector Set", e);
        }
    }

    public void delete(String summaryId) {
        try {
            redis.delete(List.of(vectorKey(summaryId), stateKey(summaryId)));
        } catch (DataAccessException ignored) {
            // TTL 是最终兜底，删除摘要不应因为缓存异常而失败。
        }
    }

    private List<TranscriptChunk> parseSearchResult(Object raw) {
        if (!(raw instanceof List<?> result)) {
            return List.of();
        }
        List<TranscriptChunk> chunks = new ArrayList<>();
        for (int i = 0; i + 1 < result.size(); i += 2) {
            try {
                JsonNode attr = objectMapper.readTree(text(result.get(i + 1)));
                String chunkText = attr.path("text").asText("");
                if (chunkText.isBlank()) {
                    continue;
                }
                chunks.add(new TranscriptChunk(
                        attr.path("chunkIndex").asInt(Integer.parseInt(text(result.get(i)))),
                        chunkText));
            } catch (JsonProcessingException | NumberFormatException ignored) {
                // 跳过损坏的单条向量属性，其余切片仍可参与回答。
            }
        }
        return List.copyOf(chunks);
    }

    private String chunkAttribute(TranscriptChunk chunk) {
        try {
            return objectMapper.writeValueAsString(new ChunkAttribute(chunk.text(), chunk.index()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化字幕切片属性", e);
        }
    }

    private String expectedState() {
        return embeddingProperties.getModelName() + ":" + embeddingProperties.getDimension();
    }

    private static String vectorKey(String summaryId) {
        return VECTOR_PREFIX + summaryId;
    }

    private static String stateKey(String summaryId) {
        return STATE_PREFIX + summaryId;
    }

    private static ApiException unavailable(String message, Throwable cause) {
        log.warn("{}：{}", message, cause.getMessage());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message + "，请稍后重试");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static RedisCommands<byte[], byte[]> nativeCommands(RedisConnection connection) {
        RedisAsyncCommands<byte[], byte[]> async =
                (RedisAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
        return async.getStatefulConnection().sync();
    }

    private static byte[] floatVector(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private record ChunkAttribute(String text, int chunkIndex) {
    }
}
