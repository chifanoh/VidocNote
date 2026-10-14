package com.vidoc.payment;

import com.vidoc.config.AlipayProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AlipayGatewayTest {

    private static String encode(byte[] key) {
        return Base64.getEncoder().encodeToString(key);
    }

    private static AlipayGateway gatewayWith(AlipayProperties properties) {
        return new AlipayGateway(properties);
    }

    /** 按支付宝的规则拼待签串：去掉 sign / sign_type，其余按键名排序后 k=v&k=v */
    private static String signContent(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .filter(e -> !"sign".equals(e.getKey()) && !"sign_type".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private static void sign(Map<String, String> params, PrivateKey key) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(signContent(params).getBytes(StandardCharsets.UTF_8));
        params.put("sign", encode(signature.sign()));
        params.put("sign_type", "RSA2");
    }

    private static Map<String, String> notify(String outTradeNo, String totalAmount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", "2021000000000000");
        params.put("out_trade_no", outTradeNo);
        params.put("trade_no", "2026092522001234567890");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", totalAmount);
        params.put("charset", "UTF-8");
        return params;
    }

    @Test
    void verifiesANotificationSignedByTheAlipayKey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AlipayProperties properties = new AlipayProperties();
        properties.setAlipayPublicKey(encode(pair.getPublic().getEncoded()));

        Map<String, String> params = notify("a".repeat(32), "19.90");
        sign(params, pair.getPrivate());

        assertThat(gatewayWith(properties).verifyNotify(params)).isTrue();
    }

    @Test
    void rejectsATamperedAmount() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AlipayProperties properties = new AlipayProperties();
        properties.setAlipayPublicKey(encode(pair.getPublic().getEncoded()));

        Map<String, String> params = notify("a".repeat(32), "19.90");
        sign(params, pair.getPrivate());
        params.put("total_amount", "0.01");

        assertThat(gatewayWith(properties).verifyNotify(params)).isFalse();
    }

    @Test
    void rejectsASignatureFromAnotherKey() throws Exception {
        KeyPair alipay = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair attacker = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AlipayProperties properties = new AlipayProperties();
        properties.setAlipayPublicKey(encode(alipay.getPublic().getEncoded()));

        Map<String, String> params = notify("b".repeat(32), "189.00");
        sign(params, attacker.getPrivate());

        assertThat(gatewayWith(properties).verifyNotify(params)).isFalse();
    }

    @Test
    void rejectsEverythingWhenNoPublicKeyIsConfigured() {
        Map<String, String> params = notify("c".repeat(32), "19.90");
        params.put("sign", "whatever");
        params.put("sign_type", "RSA2");

        assertThat(gatewayWith(new AlipayProperties()).verifyNotify(params)).isFalse();
    }

    @Test
    void isNotConfiguredWithoutCredentials() {
        AlipayProperties properties = new AlipayProperties();
        assertThat(gatewayWith(properties).isConfigured()).isFalse();

        properties.setAppId("2021000000000000");
        assertThat(gatewayWith(properties).isConfigured()).isFalse();

        properties.setEnabled(false);
        properties.setPrivateKey("x");
        properties.setAlipayPublicKey("y");
        assertThat(gatewayWith(properties).isConfigured()).isFalse();
    }

    @Test
    void switchesGatewayWithTheSandboxFlag() {
        AlipayProperties sandbox = new AlipayProperties();
        sandbox.setSandbox(true);
        assertThat(sandbox.resolvedGateway()).contains("sandbox");

        AlipayProperties prod = new AlipayProperties();
        assertThat(prod.resolvedGateway()).isEqualTo("https://openapi.alipay.com/gateway.do");

        prod.setGateway("https://example.com/gateway.do");
        assertThat(prod.resolvedGateway()).isEqualTo("https://example.com/gateway.do");
    }
}
