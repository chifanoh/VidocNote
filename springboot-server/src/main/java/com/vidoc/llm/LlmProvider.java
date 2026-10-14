package com.vidoc.llm;

/**
 * LLM 提供商抽象：输入视频转录文本，输出摘要文本。
 * 实现：Claude（Anthropic Messages API）、OpenAI 兼容接口（DeepSeek 等）。
 */
public interface LlmProvider {

    /**
     * 生成摘要。
     *
     * @param transcript 视频字幕转录文本
     * @return 摘要文本 + 是否被 max_tokens 截断
     * @throws com.vidoc.llm.LlmException 调用失败（网络/鉴权/超时等）
     */
    Result summarize(String transcript);

    /**
     * 被 max_tokens 截断的输出照样交付：token 已经花掉了，丢掉等于白付。{@code truncated=true}
     * 只表示末尾可能被切掉，由调用方入库并提示用户。
     */
    record Result(String text, boolean truncated) {
    }
}
