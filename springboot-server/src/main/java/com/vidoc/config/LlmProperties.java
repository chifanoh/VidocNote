package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 线路配置：全站一条线路（provider / api-key / base-url / model 与三个生成参数）。
 * 缺失 api-key 不在启动时阻断（本地可先验证 DB/表），由 provider 在首次调用时报错。
 */
@ConfigurationProperties(prefix = "vidoc.llm")
@Getter
@Setter
public class LlmProperties {

    /** 传入 LLM 的转录文本最大字符数，超出则截断并置 truncated */
    private long maxInputChars = 80_000;

    /** claude | openai-compatible */
    private String provider = "openai-compatible";

    private String apiKey = "";

    private String baseUrl = "";

    private String model = "";

    private int maxOutputTokens = 16384;

    /** 只有允许关思考的模型才能置 true；强制思考的模型会被服务端拒（GLM-5.x 报 1210） */
    private boolean disableThinking = false;

    /** 思考量档位：low / high / max；留空表示不发该字段 */
    private String reasoningEffort = "";

    public boolean isConfigured() {
        return model != null && !model.isBlank();
    }
}
