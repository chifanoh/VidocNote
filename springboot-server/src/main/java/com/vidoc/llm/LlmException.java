package com.vidoc.llm;

/**
 * LLM 调用失败（网络 / 鉴权 / 超时 / 响应异常）。
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
