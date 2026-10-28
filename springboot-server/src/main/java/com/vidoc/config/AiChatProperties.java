package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 问答模型线路。与摘要使用的 {@code vidoc.llm} 独立配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai.chat")
public class AiChatProperties {

    private String apiKey = "";

    private String baseUrl = "";

    private String modelName = "";

    private int maxOutputTokens = 2048;

    private double temperature = 0.2;

    private long timeoutSeconds = 60;

    private int maxRetries = 1;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey)
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(modelName);
    }
}
