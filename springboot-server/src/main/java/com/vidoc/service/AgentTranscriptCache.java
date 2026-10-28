package com.vidoc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidoc.config.AgentRagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AgentTranscriptCache {

    private static final Logger log = LoggerFactory.getLogger(AgentTranscriptCache.class);
    private static final String KEY_PREFIX = "vidoc:agent:transcript:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AgentRagProperties properties;

    public AgentTranscriptCache(StringRedisTemplate redis, ObjectMapper objectMapper, AgentRagProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void put(String summaryId, String text, boolean truncated) {
        try {
            redis.opsForValue().set(key(summaryId), objectMapper.writeValueAsString(
                    new CachedTranscript(text, truncated)), properties.getTranscriptTtl());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法写入字幕缓存", e);
        }
    }

    public Optional<CachedTranscript> get(String summaryId) {
        String value = redis.opsForValue().get(key(summaryId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            CachedTranscript cached = objectMapper.readValue(value, CachedTranscript.class);
            return cached.text() == null || cached.text().isBlank() ? Optional.empty() : Optional.of(cached);
        } catch (JsonProcessingException e) {
            log.warn("字幕缓存格式无效，已忽略：summaryId={}", summaryId);
            redis.delete(key(summaryId));
            return Optional.empty();
        }
    }

    public void delete(String summaryId) {
        redis.delete(key(summaryId));
    }

    private static String key(String summaryId) {
        return KEY_PREFIX + summaryId;
    }

    public record CachedTranscript(String text, boolean truncated) {
    }
}
