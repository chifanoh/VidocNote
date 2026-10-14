package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vidoc.alipay")
public class AlipayProperties {

    private static final String PROD_GATEWAY = "https://openapi.alipay.com/gateway.do";
    private static final String SANDBOX_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /** 功能开关。关闭或凭据不全时，下单接口返回 503 */
    private boolean enabled = true;

    /** 沙箱环境：网关与 APPID 都换一套，与线上凭据互不覆盖 */
    private boolean sandbox = false;

    private String appId = "";

    private String privateKey = "";

    private String alipayPublicKey = "";

    /** 留空则按 {@link #sandbox} 取默认网关 */
    private String gateway = "";

    /** 异步通知地址，必须公网可访问；支付宝按此 POST 支付结果 */
    private String notifyUrl;

    /** 同步跳转地址。支付结果不以此为准 */
    private String returnUrl;

    /** 订单支付超时（分钟），同时是本地惰性关闭的依据 */
    private int orderTimeoutMinutes = 120;

    public boolean isConfigured() {
        return enabled
                && appId != null && !appId.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && alipayPublicKey != null && !alipayPublicKey.isBlank();
    }

    public String resolvedGateway() {
        if (gateway != null && !gateway.isBlank()) {
            return gateway;
        }
        return sandbox ? SANDBOX_GATEWAY : PROD_GATEWAY;
    }
}
