package com.vidoc.service;

import com.vidoc.config.AgentRagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTextChunkerTest {

    @Test
    void splitsOnSentenceBoundariesAndKeepsOverlap() {
        AgentRagProperties properties = new AgentRagProperties();
        properties.setChunkSize(100);
        properties.setChunkOverlap(10);
        AgentTextChunker chunker = new AgentTextChunker(properties);
        List<TranscriptChunk> chunks = chunker.split("这是用于验证切片边界的一句话。".repeat(20));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).text()).endsWith("。");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text()).isNotBlank());
    }

    @Test
    void ignoresBlankInput() {
        assertThat(new AgentTextChunker(new AgentRagProperties()).split("  \n ")).isEmpty();
    }
}
