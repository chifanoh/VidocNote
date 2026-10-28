package com.vidoc.llm;

import com.vidoc.config.AiEmbeddingProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class AgentEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(AgentEmbeddingClient.class);
    private final AiEmbeddingProperties properties;
    private final OpenAiEmbeddingModel model;

    public AgentEmbeddingClient(AiEmbeddingProperties properties) {
        this.properties = properties;
        this.model = properties.isConfigured()
                ? OpenAiEmbeddingModel.builder()
                        .apiKey(properties.getApiKey())
                        .baseUrl(properties.getBaseUrl())
                        .modelName(properties.getModelName())
                        .dimensions(properties.getDimension())
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        .maxRetries(properties.getMaxRetries())
                        .maxSegmentsPerBatch(properties.getBatchSize())
                        .logRequests(false)
                        .logResponses(false)
                        .build()
                : null;
        if (model == null) {
            log.warn("AI 问答 Embedding 模型未配置；问答接口将返回 503");
        }
    }

    public List<float[]> embedAll(List<String> texts) {
        if (model == null) {
            throw new LlmException(HttpStatus.SERVICE_UNAVAILABLE, "问答向量模型尚未配置");
        }
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        List<Embedding> embeddings = model.embedAll(segments).content();
        if (embeddings.size() != texts.size()) {
            throw new LlmException(HttpStatus.BAD_GATEWAY, "向量模型返回数量不正确");
        }
        return embeddings.stream()
                .map(Embedding::vector)
                .peek(vector -> {
                    if (vector.length != properties.getDimension()) {
                        throw new LlmException(HttpStatus.BAD_GATEWAY,
                                "向量维度不匹配：配置为 " + properties.getDimension()
                                        + "，模型返回 " + vector.length);
                    }
                })
                .toList();
    }

    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }
}
