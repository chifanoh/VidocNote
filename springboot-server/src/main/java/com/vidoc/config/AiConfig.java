package com.vidoc.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置类（Bean注入）
 */
@Configuration
public class AiConfig {

    @Value("${ai.chat.api-key:}")
    private String apiKey;

    @Value("${ai.chat.base-url:}")
    private String baseUrl;

    @Value("${ai.chat.model-name:}")
    private String modelName;

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
