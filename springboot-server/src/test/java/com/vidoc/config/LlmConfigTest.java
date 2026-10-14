package com.vidoc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConfigTest {

    private static LlmProperties properties() {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey("test-key");
        properties.setModel("glm-4-flashx-250414");
        return properties;
    }

    @Test
    void buildsTheOpenAiCompatibleProvider() {
        LlmProperties properties = properties();

        assertThatCode(() -> new LlmConfig().llmProvider(properties)).doesNotThrowAnyException();
    }

    @Test
    void buildsTheClaudeProvider() {
        LlmProperties properties = properties();
        properties.setProvider("claude");
        properties.setBaseUrl("https://api.anthropic.com");

        assertThatCode(() -> new LlmConfig().llmProvider(properties)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnknownProviderWithItsValue() {
        LlmProperties properties = properties();
        properties.setProvider("gemini");

        assertThatThrownBy(() -> new LlmConfig().llmProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini");
    }

    /** 缺 key 不在启动时阻断：本地可以先只验证库表，首次调用才报错 */
    @Test
    void aBlankApiKeyStillBuildsTheProvider() {
        LlmProperties properties = properties();
        properties.setApiKey("  ");

        assertThatCode(() -> new LlmConfig().llmProvider(properties)).doesNotThrowAnyException();
    }
}
