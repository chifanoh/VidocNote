package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 问答检索使用的 Embedding 线路。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai.embedding")
public class AiEmbeddingProperties {

    private String apiKey = "";

    private String baseUrl = "";

    private String modelName = "";

    private int dimension = 1024;

    private int batchSize = 32;

    private long timeoutSeconds = 60;

    private int maxRetries = 1;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey)
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(modelName)
                && dimension > 0;
    }
}
