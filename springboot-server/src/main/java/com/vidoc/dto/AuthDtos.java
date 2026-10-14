package com.vidoc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 255, message = "邮箱过长")
            String email,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 72, message = "密码长度需在 6-72 之间")
            String password) {
    }

    public record UserResponse(
            Long id,
            String email,
            Instant createdAt) {
    }

    public record AuthResponse(
            String token,
            UserResponse user) {
    }
}
