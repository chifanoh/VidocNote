package com.vidoc.llm;

import org.springframework.http.HttpStatus;

/**
 * LLM 调用失败（网络 / 鉴权 / 超时 / 响应异常）。
 */
public class LlmException extends RuntimeException {

    private final HttpStatus status;

    public LlmException(String message) {
        this(HttpStatus.BAD_GATEWAY, message);
    }

    public LlmException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public LlmException(String message, Throwable cause) {
        this(HttpStatus.BAD_GATEWAY, message, cause);
    }

    public LlmException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
