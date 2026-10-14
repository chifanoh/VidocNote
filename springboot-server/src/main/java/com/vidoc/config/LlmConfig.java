package com.vidoc.config;

import com.vidoc.llm.ClaudeLlmProvider;
import com.vidoc.llm.LlmProvider;
import com.vidoc.llm.OpenAiCompatibleLlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 装配唯一的 LLM 线路（{@code vidoc.llm}）。
 * 缺失 api-key 不在启动时阻断（本地可先验证 DB/表），而由 provider 在首次调用时报错。
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmProvider llmProvider(LlmProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.warn("LLM 线路（{}）未配置 API Key，首次生成摘要会失败；配置后重启生效", properties.getModel());
        }
        LlmProvider provider = switch (properties.getProvider()) {
            case "claude" -> new ClaudeLlmProvider(properties);
            case "openai-compatible" -> new OpenAiCompatibleLlmProvider(properties);
            default -> throw new IllegalStateException("provider 仅支持 claude 或 openai-compatible，当前值："
                    + properties.getProvider());
        };
        log.info("LLM 线路已启用：{} / 模型 {}，max_tokens {}，关思考 {}，effort {}",
                properties.getProvider(), properties.getModel(), properties.getMaxOutputTokens(),
                properties.isDisableThinking(),
                StringUtils.hasText(properties.getReasoningEffort()) ? properties.getReasoningEffort() : "(未设)");
        return provider;
    }
}
