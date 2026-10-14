package com.vidoc.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidoc.config.LlmProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OpenAI 兼容提供商：{@code POST /chat/completions}（兼容 DeepSeek / OpenAI / 各类代理）。
 * 输出取 {@code choices[0].message.content}。
 */
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;
    private final String model;
    private final String apiKey;
    private final int maxOutputTokens;
    private final boolean disableThinking;
    private final String reasoningEffort;

    public OpenAiCompatibleLlmProvider(LlmProperties config) {
        this.apiKey = config.getApiKey();
        this.client = RestClient.builder()
                .baseUrl(buildEndpoint(config.getBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = config.getModel();
        this.maxOutputTokens = config.getMaxOutputTokens();
        this.disableThinking = config.isDisableThinking();
        this.reasoningEffort = config.getReasoningEffort();
    }
    /** 最大尝试次数（含首次）。Flash 类廉价模型常瞬时过载（HTTP 429「模型访问量过大」/5xx），需退避重试。
     * 过载窗口常达数秒～数十秒，故用指数退避并放宽到 ~30s（生成跑在异步线程，阻塞可接受）。 */
    private static final int MAX_ATTEMPTS = 5;
    /** 退避基数：第 n 次重试前睡 {@code BASE_DELAY_MS * 2^(n-1)} 毫秒（2s/4s/8s/16s）。 */
    private static final long BASE_DELAY_MS = 2_000;
    //TODO 上线时修改
    @Override
    public LlmProvider.Result summarize(String transcript) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("未配置 OpenAI 兼容 API Key（VIDOC_LLM_API_KEY）");
        }
        Map<String, Object> body = buildBody(transcript);
        LlmException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                sleepQuietly(BASE_DELAY_MS * (1L << (attempt - 1)));
            }
            try {
                String json = client.post()
                        .body(body)
                        .retrieve()
                        .onStatus(status -> status.isError(), (request, response) -> {
                            int code = response.getStatusCode().value();
                            if (code == 429 || code >= 500) {
                                throw new LlmTransientException(
                                        "LLM API 瞬时过载（HTTP " + code + "），可重试");
                            }
                            throw new LlmException("LLM API 返回 " + response.getStatusCode()
                                    + "：请检查 VIDOC_LLM_OPENAI_API_KEY 与账户额度");
                        })
                        .body(String.class);
                return extract(json);
            } catch (LlmTransientException e) {
                last = e; // 过载 → 退避后重试
            }
        }
        throw new LlmException("LLM API 重试 " + MAX_ATTEMPTS + " 次后仍不可用："
                + (last == null ? "未知错误" : last.getMessage()));
    }

    /** 瞬时错误（429/5xx）：触发重试，不直接判定失败。 */
    private static final class LlmTransientException extends LlmException {
        private LlmTransientException(String message) {
            super(message);
        }
    }

    private Map<String, Object> buildBody(String transcript) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("max_tokens", maxOutputTokens);
        if (disableThinking) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort.trim());
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", PromptTemplates.SYSTEM_PROMPT),
                Map.of("role", "user", "content",
                        PromptTemplates.buildUserMessage(transcript))));
        return body;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 从 chat/completions 响应中取出 choices[0].message.content。
     * {@code finish_reason=length} 表示撞上 max_tokens 被硬截断 —— 正文多半是半截，但**这批 token 已经花掉了**，
     * 所以照旧返回并置 {@code truncated=true}，由调用方入库并提示；当失败丢掉才是真浪费。
     * 缺该字段（部分代理不返回）时不判截断，只认显式的 length。
     */
    static LlmProvider.Result extract(String json) {
        if (json == null || json.isBlank()) {
            throw new LlmException("LLM API 返回空响应");
        }
        try {
            JsonNode choice = MAPPER.readTree(json).path("choices").path(0);
            String finishReason = choice.path("finish_reason").asText(null);
            String content = stripInlineThinking(choice.path("message").path("content").asText());
            if (content == null || content.isBlank()) {
                throw new LlmException("LLM API 响应中无文本内容（finish_reason=" + finishReason + "）");
            }
            return new LlmProvider.Result(content.trim(), "length".equals(finishReason));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM API 响应解析失败：" + e.getMessage(), e);
        }
    }

    private static final Pattern INLINE_THINKING = Pattern.compile("(?s)<think>.*?</think>");

    /**
     * glm-5.3-flash 这类模型把思维链**内联在 content 里**（不是 reasoning_content），且
     * thinking.type=disabled 对它无效（实测 2026-09-26：两个请求都返回 &lt;think&gt;…&lt;/think&gt;）。
     * 不剥掉的话整段推理会进摘要正文。GLM-5.3 系列不受影响（思维链在独立字段里）。
     */
    static String stripInlineThinking(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String stripped = INLINE_THINKING.matcher(content).replaceAll("");
        int unterminated = stripped.indexOf("<think>");
        if (unterminated >= 0) {
            // 被 max_tokens 截断的思维链：从开标签起全部丢掉，留着只会是半截推理
            stripped = stripped.substring(0, unterminated);
        }
        return stripped.trim();
    }

    private String buildEndpoint(String baseUrl) {
        String base = baseUrl != null ? baseUrl.trim() : "";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/chat/completions";
    }
}
