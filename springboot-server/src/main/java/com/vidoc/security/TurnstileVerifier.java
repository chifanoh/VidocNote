package com.vidoc.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidoc.config.TurnstileProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 人机验证校验。解析固定走服务端：小部件的 token 只证明「这边取到了」，真伪必须问 Cloudflare。
 * 未配置时一律放行（本地开发），配置了则一律失败关闭 —— 连不上也按不通过处理，不静默放行。
 */
@Service
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final TurnstileProperties properties;
    private final RestClient client;

    public TurnstileVerifier(TurnstileProperties properties, RestClient turnstileRestClient) {
        this.properties = properties;
        this.client = turnstileRestClient;
        if (!properties.isConfigured()) {
            log.info("人机验证未启用（VIDOC_TURNSTILE_ENABLED / VIDOC_TURNSTILE_SECRET_KEY 未配全），登录接口不做校验");
        }
    }

    public boolean verify(String token) {
        if (!properties.isConfigured()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey());
        form.add("response", token);

        try {
            SiteverifyResponse response = client.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteverifyResponse.class);
            if (response == null || !response.success()) {
                log.warn("人机验证未通过：{}", response == null ? "空响应" : response.errorCodes());
                return false;
            }
            if (isSet(properties.getExpectedHostname())
                    && !properties.getExpectedHostname().equals(response.hostname())) {
                log.warn("人机验证 hostname 不符：期望 {}，实际 {}",
                        properties.getExpectedHostname(), response.hostname());
                return false;
            }
            if (isSet(properties.getExpectedAction())
                    && !properties.getExpectedAction().equals(response.action())) {
                log.warn("人机验证 action 不符：期望 {}，实际 {}",
                        properties.getExpectedAction(), response.action());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("人机验证请求失败，按未通过处理：{}", e.getMessage());
            return false;
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteverifyResponse(
            boolean success,
            String hostname,
            String action,
            @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
