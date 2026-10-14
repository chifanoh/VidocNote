package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Turnstile 人机验证。默认关闭：没配 secret 时登录接口完全不校验，本地与测试不受影响。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "vidoc.turnstile")
public class TurnstileProperties {

    private boolean enabled = false;

    private String secretKey = "";

    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    /** 留空则不核对 */
    private String expectedHostname = "";

    /** 留空则不核对；小部件与服务端要填同一个值才有意义 */
    private String expectedAction = "";

    public boolean isConfigured() {
        return enabled && secretKey != null && !secretKey.isBlank();
    }
}
