package com.vidoc.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String completion(String content, String finishReason) throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        }
        choice.put("message", message);

        return MAPPER.writeValueAsString(Map.of("choices", List.of(choice)));
    }

    @Test
    void returnsTheAnswerWhenTheModelStopsNormally() throws Exception {
        LlmProvider.Result result = OpenAiCompatibleLlmProvider.extract(
                completion("## 摘要\n\n正文", "stop"));

        assertThat(result.text()).isEqualTo("## 摘要\n\n正文");
        assertThat(result.truncated()).isFalse();
    }

    /** glm-5.3-flash 的真实返回形状（实测 2026-09-26） */
    @Test
    void stripsInlineThinkingBeforeTheAnswer() throws Exception {
        assertThat(OpenAiCompatibleLlmProvider.extract(
                completion("<think>\n用户让我只回复两个字…\n</think>\n正常", "stop")).text())
                .isEqualTo("正常");
    }

    /** 撞上 max_tokens 时正文是半截，但这批 token 已经花掉了 —— 照样交付、只标记截断，不当失败丢掉 */
    @Test
    void keepsAnAnswerCutOffByMaxTokensAndFlagsIt() throws Exception {
        LlmProvider.Result result = OpenAiCompatibleLlmProvider.extract(
                completion("## 摘要\n\n视频主要讲了……（说到一半没了", "length"));

        assertThat(result.text()).isEqualTo("## 摘要\n\n视频主要讲了……（说到一半没了");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void rejectsAResponseWithNoTextLeft() throws Exception {
        assertThatThrownBy(() -> OpenAiCompatibleLlmProvider.extract(completion("", "stop")))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("无文本内容");
    }

    /** 只剩思维链（被截断或模型没给答案）没有东西可交付，仍是失败 */
    @Test
    void rejectsAResponseThatIsOnlyThinking() throws Exception {
        assertThatThrownBy(() -> OpenAiCompatibleLlmProvider.extract(
                completion("<think>还没想完就被截断了", "length")))
                .isInstanceOf(LlmException.class);
    }

    /** 部分代理不返回 finish_reason，不能因此判截断 */
    @Test
    void toleratesAMissingFinishReason() throws Exception {
        LlmProvider.Result result = OpenAiCompatibleLlmProvider.extract(completion("正文", null));

        assertThat(result.text()).isEqualTo("正文");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void rejectsAnEmptyResponse() {
        assertThatThrownBy(() -> OpenAiCompatibleLlmProvider.extract("  "))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void stripsEveryThinkingBlock() {
        assertThat(OpenAiCompatibleLlmProvider.stripInlineThinking(
                "<think>第一段</think>正文<think>第二段</think>结尾"))
                .isEqualTo("正文结尾");
    }

    @Test
    void dropsAnUnterminatedThinkingBlock() {
        assertThat(OpenAiCompatibleLlmProvider.stripInlineThinking("<think>还没想完就被截断了"))
                .isEmpty();
        assertThat(OpenAiCompatibleLlmProvider.stripInlineThinking("正文<think>被截断"))
                .isEqualTo("正文");
    }

    /** GLM-5.3 系列把思维链放在 reasoning_content 里，content 是干净的，不能被误伤 */
    @Test
    void keepsPlainContentUntouched() {
        assertThat(OpenAiCompatibleLlmProvider.stripInlineThinking("## 摘要\n\n正文"))
                .isEqualTo("## 摘要\n\n正文");
    }

    @Test
    void toleratesNullAndEmpty() {
        assertThat(OpenAiCompatibleLlmProvider.stripInlineThinking(null)).isNull();
        assertThat(OpenAiCompatibleLlmProvider.stripInlineThinking("")).isEmpty();
    }
}
