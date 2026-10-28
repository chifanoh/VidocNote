package com.vidoc.service;

import com.vidoc.config.AgentRagProperties;
import com.vidoc.entity.VideoSummary;
import com.vidoc.exception.ApiException;
import com.vidoc.llm.AgentEmbeddingClient;
import com.vidoc.video.VideoSource;
import com.vidoc.video.VideoSourceRegistry;
import com.vidoc.ytdlp.YtDlpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AgentRagService {

    private static final Logger log = LoggerFactory.getLogger(AgentRagService.class);
    private static final int LOCK_STRIPES = 64;

    private final AgentTranscriptCache transcriptCache;
    private final AgentTextChunker chunker;
    private final AgentEmbeddingClient embeddingClient;
    private final AgentVectorStore vectorStore;
    private final VideoSourceRegistry videoSourceRegistry;
    private final AgentRagProperties properties;
    private final ReentrantLock[] buildLocks = new ReentrantLock[LOCK_STRIPES];

    public AgentRagService(AgentTranscriptCache transcriptCache, AgentTextChunker chunker,
                           AgentEmbeddingClient embeddingClient, AgentVectorStore vectorStore,
                           VideoSourceRegistry videoSourceRegistry, AgentRagProperties properties) {
        this.transcriptCache = transcriptCache;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.videoSourceRegistry = videoSourceRegistry;
        this.properties = properties;
        for (int i = 0; i < buildLocks.length; i++) {
            buildLocks[i] = new ReentrantLock();
        }
    }

    /**
     * 查缓存转向量检索
     * @param summary
     * @param question
     * @return
     */
    public List<TranscriptChunk> retrieve(VideoSummary summary, String question) {
        ensureIndexed(summary);
        float[] questionVector = embeddingClient.embed(question);
        List<TranscriptChunk> chunks = vectorStore.search(summary.getPublicId(), questionVector);
        if (!chunks.isEmpty()) {
            return chunks;
        }

        // 索引状态可能仍在，但底层 key 已被单独驱逐；重建一次，避免一次偶发驱逐把问答永久卡住。
        rebuild(summary);
        return vectorStore.search(summary.getPublicId(), questionVector);
    }

    private void ensureIndexed(VideoSummary summary) {
        if (vectorStore.isReady(summary.getPublicId())) {
            return;
        }
        rebuild(summary);
    }

    private void rebuild(VideoSummary summary) {
        ReentrantLock lock = lockFor(summary.getPublicId());
        lock.lock();
        try {
            if (vectorStore.isReady(summary.getPublicId())) {
                return;
            }
            String transcript = transcriptCache.get(summary.getPublicId())
                    .map(AgentTranscriptCache.CachedTranscript::text)
                    .orElseGet(() -> restoreTranscript(summary));
            List<TranscriptChunk> chunks = chunker.split(transcript);
            if (chunks.isEmpty()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "该视频没有可用于问答的字幕");
            }
            List<float[]> vectors = embeddingClient.embedAll(chunks.stream().map(TranscriptChunk::text).toList());
            vectorStore.store(summary.getPublicId(), chunks, vectors);
            log.info("问答索引已建立：summaryId={}，切片 {} 个", summary.getPublicId(), chunks.size());
        } catch (RedisSystemException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "问答缓存暂时不可用，请稍后重试");
        } finally {
            lock.unlock();
        }
    }

    private String restoreTranscript(VideoSummary summary) {
        try {
            VideoSource source = videoSourceRegistry.resolve(summary.getVideoUrl());
            VideoSource.Transcript transcript = source.transcript(
                            summary.getVideoUrl(), properties.getMaxTranscriptChars())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "该视频当前没有可用字幕，暂时无法回答"));
            transcriptCache.put(summary.getPublicId(), transcript.text(), transcript.truncated());
            return transcript.text();
        } catch (ApiException e) {
            throw e;
        } catch (YtDlpException e) {
            log.warn("问答字幕恢复失败：summaryId={}，原因={}", summary.getPublicId(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "字幕抓取失败，请稍后重试");
        } catch (RuntimeException e) {
            log.warn("问答缓存恢复失败：summaryId={}，原因={}", summary.getPublicId(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "问答缓存暂时不可用，请稍后重试");
        }
    }

    private ReentrantLock lockFor(String summaryId) {
        return buildLocks[Math.floorMod(summaryId.hashCode(), buildLocks.length)];
    }
}
