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

/**
 * Claude 提供商：Anthropic Messages API（{@code POST /v1/messages}）。
 * 端点与版本头见 Anthropic 官方文档；输出取 content 中 type=text 的块拼接。
 */
public class ClaudeLlmProvider implements LlmProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 详细摘要需要长输出。 */
    private static final int MAX_TOKENS = 8192;

    private final RestClient client;
    private final String model;
    private final String apiKey;

    public ClaudeLlmProvider(LlmProperties config) {
        this.apiKey = config.getApiKey();
        this.client = RestClient.builder()
                .baseUrl(stripTrailingSlash(config.getBaseUrl()) + "/v1/messages")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
        this.model = config.getModel();
    }

    @Override
    public LlmProvider.Result summarize(String transcript) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("未配置 Claude API Key（VIDOC_LLM_API_KEY）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", PromptTemplates.SYSTEM_PROMPT);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", PromptTemplates.buildUserMessage(transcript))));

        String json = client.post()
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (request, response) -> {
                    throw new LlmException("Claude API 返回 " + response.getStatusCode()
                            + "：请检查 VIDOC_LLM_API_KEY 与账户额度");
                })
                .body(String.class);

        return extract(json);
    }

    /**
     * 从 Messages 响应中取出所有 type=text 块拼接（可能含 thinking 等块）。
     * {@code stop_reason=max_tokens} 说明正文是半截，但 token 已经花掉 —— 照旧交付并标记，不当失败丢弃。
     */
    private LlmProvider.Result extract(String json) {
        if (json == null || json.isBlank()) {
            throw new LlmException("Claude API 返回空响应");
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            boolean truncated = "max_tokens".equals(root.path("stop_reason").asText(null));
            JsonNode content = root.path("content");
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            String result = text.toString().trim();
            if (result.isEmpty()) {
                throw new LlmException("Claude API 响应中无文本内容");
            }
            return new LlmProvider.Result(result, truncated);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Claude API 响应解析失败：" + e.getMessage(), e);
        }
    }

    private String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
