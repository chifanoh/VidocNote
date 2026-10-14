package com.vidoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TurnstileConfig {

    /** 超时必须短：校验服务卡住不能占着登录线程 */
    @Bean
    public RestClient turnstileRestClient(RestClient.Builder builder, TurnstileProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return builder.baseUrl(properties.getVerifyUrl()).requestFactory(factory).build();
    }
}
