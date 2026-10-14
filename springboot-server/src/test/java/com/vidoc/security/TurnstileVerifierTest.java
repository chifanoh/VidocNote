package com.vidoc.security;

import com.vidoc.config.TurnstileProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileVerifierTest {

    private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private TurnstileProperties properties;
    private MockRestServiceServer cloudflare;
    private TurnstileVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new TurnstileProperties();
        properties.setEnabled(true);
        properties.setSecretKey("test-secret");
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        cloudflare = MockRestServiceServer.bindTo(builder).build();
        verifier = new TurnstileVerifier(properties, builder.build());
    }

    private void expectSiteverify(String body) {
        cloudflare.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("secret=test-secret")))
                .andExpect(content().string(Matchers.containsString("response=widget-token")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void acceptsATokenCloudflareConfirms() {
        expectSiteverify("{\"success\":true}");

        assertThat(verifier.verify("widget-token")).isTrue();
        cloudflare.verify();
    }

    @Test
    void rejectsATokenCloudflareRefuses() {
        expectSiteverify("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");

        assertThat(verifier.verify("widget-token")).isFalse();
        cloudflare.verify();
    }

    @Test
    void rejectsAMissingTokenWithoutAskingCloudflare() {
        assertThat(verifier.verify(null)).isFalse();
        assertThat(verifier.verify("  ")).isFalse();
    }

    /** 校验服务挂了宁可登录失败，也不能静默放行 */
    @Test
    void rejectsWhenCloudflareIsUnreachable() {
        cloudflare.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(verifier.verify("widget-token")).isFalse();
        cloudflare.verify();
    }

    @Test
    void rejectsATokenSolvedOnAnotherHostname() {
        properties.setExpectedHostname("vidocnote.com");
        expectSiteverify("{\"success\":true,\"hostname\":\"evil.example\"}");

        assertThat(verifier.verify("widget-token")).isFalse();
    }

    @Test
    void rejectsATokenSolvedForAnotherAction() {
        properties.setExpectedAction("login");
        expectSiteverify("{\"success\":true,\"action\":\"signup\"}");

        assertThat(verifier.verify("widget-token")).isFalse();
    }

    @Test
    void acceptsWhenHostnameAndActionMatch() {
        properties.setExpectedHostname("vidocnote.com");
        properties.setExpectedAction("login");
        expectSiteverify("{\"success\":true,\"hostname\":\"vidocnote.com\",\"action\":\"login\"}");

        assertThat(verifier.verify("widget-token")).isTrue();
        cloudflare.verify();
    }

    @Test
    void skipsVerificationEntirelyWhenNotEnabled() {
        properties.setEnabled(false);

        assertThat(verifier.verify(null)).isTrue();
    }

    @Test
    void skipsVerificationEntirelyWhenSecretIsMissing() {
        properties.setSecretKey("");

        assertThat(verifier.verify(null)).isTrue();
    }
}
