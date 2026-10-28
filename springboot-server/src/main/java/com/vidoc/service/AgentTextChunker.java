package com.vidoc.service;

import com.vidoc.config.AgentRagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentTextChunker {

    private static final String BREAK_CHARS = "。！？!?；;\n";

    private final AgentRagProperties properties;

    public AgentTextChunker(AgentRagProperties properties) {
        this.properties = properties;
    }

    public List<TranscriptChunk> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int chunkSize = Math.max(properties.getChunkSize(), 100);
        int overlap = Math.min(Math.max(properties.getChunkOverlap(), 0), chunkSize / 2);
        String normalized = text.replace("\r\n", "\n").trim();
        List<TranscriptChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int hardEnd = Math.min(start + chunkSize, normalized.length());
            int end = hardEnd;
            if (hardEnd < normalized.length()) {
                int searchFrom = start + chunkSize * 3 / 4;
                int breakAt = lastBreak(normalized, searchFrom, hardEnd);
                if (breakAt > start) {
                    end = breakAt + 1;
                }
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(new TranscriptChunk(chunks.size(), chunk));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return List.copyOf(chunks);
    }

    private int lastBreak(String text, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            if (BREAK_CHARS.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
