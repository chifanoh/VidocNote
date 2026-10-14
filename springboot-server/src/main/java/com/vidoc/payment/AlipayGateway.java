package com.vidoc.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.vidoc.config.AlipayProperties;
import com.vidoc.entity.Order;
import com.vidoc.entity.Plan;
import com.vidoc.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 支付宝电脑网站支付的唯一出口：下单取表单、查询交易状态、验签异步通知。
 * 任何地方的"支付成功"都必须经这里确认。
 */
@Service
public class AlipayGateway {

    private static final Logger log = LoggerFactory.getLogger(AlipayGateway.class);

    /** 电脑网站支付在电脑场景下唯一支持的产品码 */
    static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";

    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";

    /** time_expire 用北京时间，与签名的 charset 无关，是支付宝要求的字面格式 */
    private static final DateTimeFormatter TIME_EXPIRE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    /** 交易不存在时支付宝返回的错误码，属于正常分支而非故障 */
    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    private final AlipayProperties properties;
    private final AlipayClient client;

    public AlipayGateway(AlipayProperties properties) {
        this.properties = properties;
        this.client = properties.isConfigured() ? build(properties) : null;
        if (client == null) {
            log.warn("支付宝未启用或凭据不全（VIDOC_ALIPAY_*），下单接口将返回 503；"
                    + "补齐 APPID / 应用私钥 / 支付宝公钥后无需改代码");
        } else {
            log.info("支付宝已启用：{}，APPID {}，网关 {}",
                    properties.isSandbox() ? "沙箱" : "生产", mask(properties.getAppId()), properties.resolvedGateway());
        }
    }

    public boolean isConfigured() {
        return client != null;
    }

    private static AlipayClient build(AlipayProperties p) {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(p.resolvedGateway());
        config.setAppId(p.getAppId());
        config.setPrivateKey(p.getPrivateKey());
        config.setFormat("json");
        config.setCharset(CHARSET);
        config.setAlipayPublicKey(p.getAlipayPublicKey());
        config.setSignType(SIGN_TYPE);
        try {
            return new DefaultAlipayClient(config);
        } catch (AlipayApiException e) {
            // 私钥格式不对等初始化问题不阻断启动，退化为「未配置」，由接口层返回 503
            log.warn("支付宝客户端初始化失败（凭据可能有误），下单接口将返回 503：{}", e.getMessage());
            return null;
        }
    }

    /** 下单并返回一段自动提交的 form HTML，交给浏览器跳收银台。 */
    public String pagePay(Order order) {
        requireConfigured();
        Plan plan = Plan.of(order.getPlanCode());
        if (plan == null) {
            throw new IllegalStateException("订单引用了未知套餐：" + order.getPlanCode());
        }

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.getOutTradeNo());
        model.setTotalAmount(plan.getTotalAmount());
        model.setSubject(plan.getSubject());
        model.setProductCode(PRODUCT_CODE);
        model.setTimeExpire(TIME_EXPIRE.format(order.getExpireAt()));

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setReturnUrl(properties.getReturnUrl());
        request.setBizModel(model);

        try {
            AlipayTradePagePayResponse response = client.pageExecute(request, "POST");
            if (!response.isSuccess()) {
                log.warn("支付宝下单失败：{} / {}", response.getCode(), response.getSubMsg());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "支付宝下单失败，请稍后重试");
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            log.warn("支付宝下单异常：{}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "支付宝下单失败，请稍后重试");
        }
    }

    /** 主动查询交易状态。交易不存在返回 {@link TradeQueryResult#notFound()} 而不是抛错。 */
    public TradeQueryResult query(String outTradeNo) {
        requireConfigured();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (TRADE_NOT_EXIST.equals(response.getCode()) || TRADE_NOT_EXIST.equals(response.getSubCode())) {
                return TradeQueryResult.notFound();
            }
            if (!response.isSuccess()) {
                log.warn("支付宝查询失败：{} / {}", response.getCode(), response.getSubMsg());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "查询支付结果失败，请稍后重试");
            }
            return new TradeQueryResult(true, response.getTradeStatus(),
                    response.getTotalAmount(), response.getTradeNo());
        } catch (AlipayApiException e) {
            log.warn("支付宝查询异常：{}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "查询支付结果失败，请稍后重试");
        }
    }

    /**
     * 异步通知验签。这是通知端点唯一的安全边界：未配置公钥或验签不通过一律 false。
     */
    public boolean verifyNotify(Map<String, String> params) {
        String publicKey = properties.getAlipayPublicKey();
        if (publicKey == null || publicKey.isBlank()) {
            return false;
        }
        try {
            return AlipaySignature.rsaCheckV1(params, publicKey, CHARSET, SIGN_TYPE);
        } catch (AlipayApiException e) {
            log.warn("支付宝通知验签异常：{}", e.getMessage());
            return false;
        }
    }

    private void requireConfigured() {
        if (client == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "支付功能暂未开放");
        }
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 2);
    }

    /** status 为支付宝的 trade_status；paid 只表示查询到了交易，不代表支付成功。 */
    public record TradeQueryResult(boolean found, String status, String totalAmount, String tradeNo) {

        static TradeQueryResult notFound() {
            return new TradeQueryResult(false, null, null, null);
        }

        public boolean isPaid() {
            return found && ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status));
        }
    }
}
