package com.vidoc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置。必须挂在过滤器层而不能用 MVC 的 addCorsMappings：CORS 预检 OPTIONS
 * 不带 Authorization 头，落到 Sa-Token 拦截器会被判未登录返 401，生产跨域即失效
 * （dev 走 Nitro 同源代理不发预检，测不出来）。CorsFilter 对预检请求直接短路响应。
 * 置最高优先级是为了排到 Sa-Token 自带过滤器（防火墙等）之前，否则它们产出的
 * 响应不会带 CORS 头，浏览器只能看到不透明的网络错误。
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${vidoc.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Turnstile-Token"));
        // 刻意不设 allowCredentials：token 只走 Authorization 头，不需要携带 cookie 的跨源请求
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
