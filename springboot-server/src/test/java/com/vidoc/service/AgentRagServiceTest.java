package com.vidoc.service;

import com.vidoc.config.AgentRagProperties;
import com.vidoc.entity.SummaryStatus;
import com.vidoc.entity.VideoSummary;
import com.vidoc.llm.AgentEmbeddingClient;
import com.vidoc.video.VideoSourceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRagServiceTest {

    private AgentTranscriptCache transcriptCache;
    private AgentEmbeddingClient embeddingClient;
    private AgentVectorStore vectorStore;
    private AgentRagService service;
    private VideoSummary summary;

    @BeforeEach
    void setUp() {
        transcriptCache = mock(AgentTranscriptCache.class);
        embeddingClient = mock(AgentEmbeddingClient.class);
        vectorStore = mock(AgentVectorStore.class);
        AgentRagProperties properties = new AgentRagProperties();
        service = new AgentRagService(transcriptCache, new AgentTextChunker(properties),
                embeddingClient, vectorStore, mock(VideoSourceRegistry.class), properties);

        summary = new VideoSummary(7L, "https://example.com/video");
        summary.setStatus(SummaryStatus.COMPLETED);
        when(vectorStore.isReady(summary.getPublicId())).thenReturn(false);
        when(transcriptCache.get(summary.getPublicId()))
                .thenReturn(Optional.of(new AgentTranscriptCache.CachedTranscript("这是一段完整字幕。", false)));
        when(embeddingClient.embedAll(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(embeddingClient.embed("问题")).thenReturn(new float[]{0.3f, 0.4f});
        when(vectorStore.search(eq(summary.getPublicId()), any(float[].class)))
                .thenReturn(List.of(new TranscriptChunk(0, "这是一段完整字幕。")));
    }

    @Test
    void buildsTheIndexFromTheTemporaryTranscriptThenSearches() {
        List<TranscriptChunk> result = service.retrieve(summary, "问题");

        assertThat(result).containsExactly(new TranscriptChunk(0, "这是一段完整字幕。"));
        verify(vectorStore).store(eq(summary.getPublicId()), anyList(), anyList());
        verify(embeddingClient).embed("问题");
    }
}
