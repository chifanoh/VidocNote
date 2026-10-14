package com.vidoc.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态码与面向用户的可读信息。
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
