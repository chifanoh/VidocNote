package com.vidoc.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 登录校验：/api/auth/** 与支付宝异步通知放行，其余 /api/** 需已登录。
 * me / logout 虽在放行前缀内，但它们方法体自己调 StpUtil，未登录时同样抛 NotLoginException → 401。
 * 通知是支付宝匿名 POST，带不了我们的令牌，它的安全边界只有验签（见 AlipayGateway#verifyNotify）。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter.match("/api/**")
                        .notMatch("/api/auth/**")
                        .notMatch("/api/payments/alipay/notify")
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/api/**");
    }
}
