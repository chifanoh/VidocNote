package com.vidoc.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.vidoc.dto.AuthDtos;
import com.vidoc.exception.ApiException;
import com.vidoc.security.TurnstileVerifier;
import com.vidoc.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 小部件拿到的 token 走请求头，不进请求体（体里只有邮箱密码，避免污染校验） */
    static final String TURNSTILE_HEADER = "X-Turnstile-Token";

    private final UserService userService;
    private final TurnstileVerifier turnstileVerifier;

    public AuthController(UserService userService, TurnstileVerifier turnstileVerifier) {
        this.userService = userService;
        this.turnstileVerifier = turnstileVerifier;
    }

    /** 登录即注册：邮箱没注册过就用这个密码直接建号，注册过则校验密码。 */
    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                       @RequestHeader(value = TURNSTILE_HEADER, required = false)
                                       String turnstileToken) {
        if (!turnstileVerifier.verify(turnstileToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "人机验证未通过，请刷新页面后重试");
        }
        return userService.loginOrRegister(request);
    }

    /** 前端刷新页面后用它恢复用户信息；未登录由 NotLoginException 统一转 401。 */
    @GetMapping("/me")
    public AuthDtos.UserResponse me() {
        return userService.currentUser(StpUtil.getLoginIdAsLong());
    }

    /** 注销服务端会话（只注销当前 token）；不调则该 token 在有效期内仍然可用。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        StpUtil.logout();
    }
}
