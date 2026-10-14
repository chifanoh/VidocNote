package com.vidoc.payment;

import com.vidoc.config.AlipayProperties;
import com.vidoc.dto.PaymentDtos;
import com.vidoc.entity.Order;
import com.vidoc.entity.OrderStatus;
import com.vidoc.entity.Plan;
import com.vidoc.exception.ApiException;
import com.vidoc.repository.OrderRepository;
import com.vidoc.service.PointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final Long USER = 7L;
    private static final String APP_ID = "2021000000000000";

    private OrderRepository orders;
    private PointsService points;
    private AlipayGateway alipay;
    private AlipayProperties properties;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        points = mock(PointsService.class);
        alipay = mock(AlipayGateway.class);
        properties = new AlipayProperties();
        properties.setAppId(APP_ID);
        properties.setOrderTimeoutMinutes(120);
        service = new OrderService(orders, points, alipay, properties, stubTransactions());
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 单测不连库，用最简的事务管理器让 TransactionTemplate 能跑通 */
    private static PlatformTransactionManager stubTransactions() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }

    private Order pending() {
        Instant now = Instant.now();
        return new Order(USER, Plan.MONTHLY, now, now.plusSeconds(7200));
    }

    private Order pendingTopUp() {
        Instant now = Instant.now();
        return new Order(USER, Plan.TOPUP_50, now, now.plusSeconds(7200));
    }

    private Map<String, String> notifyParams(String outTradeNo, String totalAmount) {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", APP_ID);
        params.put("out_trade_no", outTradeNo);
        params.put("trade_no", "2026092522001234567890");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", totalAmount);
        return params;
    }

    // ---- 下单 ----

    @Test
    void takesAmountAndQuotaOnlyFromTheServerSidePlan() {
        when(alipay.isConfigured()).thenReturn(true);
        when(points.hasActiveSubscription(USER)).thenReturn(false);

        PaymentDtos.OrderItem item = service.create(USER, "monthly");

        assertThat(item.amount()).isEqualTo(Plan.MONTHLY.getAmountFen());
        assertThat(item.planCode()).isEqualTo("monthly");
        assertThat(item.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void cannotCarryClientSuppliedAmountOrQuota() {
        assertThat(PaymentDtos.CreateOrderRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("planCode");
    }

    @Test
    void rejectsAnUnknownPlan() {
        when(alipay.isConfigured()).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, "free"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("套餐不存在");
        verify(orders, never()).save(any());
    }

    @Test
    void rejectsASecondPurchaseWhileTheSubscriptionIsActive() {
        when(alipay.isConfigured()).thenReturn(true);
        when(points.hasActiveSubscription(USER)).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, "monthly"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("有效期");
        verify(orders, never()).save(any());
    }

    @Test
    void refusesOrdersWhilePaymentIsUnavailable() {
        when(alipay.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.create(USER, "monthly"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("暂未开放");
    }

    @Test
    void allowsATopUpWhileTheSubscriptionIsStillActive() {
        when(alipay.isConfigured()).thenReturn(true);
        when(points.hasActiveSubscription(USER)).thenReturn(true);

        PaymentDtos.OrderItem item = service.create(USER, "topup_50");

        assertThat(item.planCode()).isEqualTo("topup_50");
        assertThat(item.amount()).isEqualTo(Plan.TOPUP_50.getAmountFen());
        verify(orders).save(any(Order.class));
        verify(points, never()).hasActiveSubscription(USER);
    }

    // ---- 发起支付 ----

    @Test
    void returnsTheAutoSubmittingForm() {
        Order order = pending();
        when(orders.findByOutTradeNoAndUserId(order.getOutTradeNo(), USER)).thenReturn(Optional.of(order));
        when(alipay.pagePay(order)).thenReturn("<form></form>");

        PaymentDtos.PayFormResponse response = service.pay(USER, order.getOutTradeNo());

        assertThat(response.formHtml()).isEqualTo("<form></form>");
        assertThat(response.outTradeNo()).isEqualTo(order.getOutTradeNo());
    }

    @Test
    void closesAnExpiredOrderInsteadOfPayingIt() {
        Instant now = Instant.now();
        Order order = new Order(USER, Plan.MONTHLY, now.minusSeconds(7200), now.minusSeconds(60));
        when(orders.findByOutTradeNoAndUserId(order.getOutTradeNo(), USER)).thenReturn(Optional.of(order));
        when(orders.findByOutTradeNo(order.getOutTradeNo())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.pay(USER, order.getOutTradeNo()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("超时");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        verify(alipay, never()).pagePay(any());
    }

    @Test
    void treatsSomeoneElsesOrderAsMissing() {
        String outTradeNo = "a".repeat(32);
        when(orders.findByOutTradeNoAndUserId(outTradeNo, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(USER, outTradeNo))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("订单不存在");
    }

    // ---- 异步通知 ----

    @Test
    void rejectsANotificationThatFailsVerification() {
        when(alipay.verifyNotify(any())).thenReturn(false);

        assertThat(service.handleNotify(notifyParams("a".repeat(32), Plan.MONTHLY.getTotalAmount()))).isEqualTo("failure");
        verify(orders, never()).findForUpdate(any());
        verify(points, never()).grantSubscription(anyLong(), any(), any());
    }

    @Test
    void rejectsANotificationForAnotherApp() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        Map<String, String> params = notifyParams("a".repeat(32), Plan.MONTHLY.getTotalAmount());
        params.put("app_id", "9999999999999999");

        assertThat(service.handleNotify(params)).isEqualTo("failure");
        verify(orders, never()).findForUpdate(any());
    }

    @Test
    void rejectsAMismatchedAmount() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        Order order = pending();
        when(orders.findForUpdate(order.getOutTradeNo())).thenReturn(Optional.of(order));

        assertThat(service.handleNotify(notifyParams(order.getOutTradeNo(), "999.99"))).isEqualTo("failure");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(points, never()).grantSubscription(anyLong(), any(), any());
    }

    @Test
    void acknowledgesTradesThatHaveNotSucceededYet() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        Map<String, String> params = notifyParams("b".repeat(32), Plan.MONTHLY.getTotalAmount());
        params.put("trade_status", "WAIT_BUYER_PAY");

        assertThat(service.handleNotify(params)).isEqualTo("success");
        verify(orders, never()).findForUpdate(any());
    }

    @Test
    void confirmsPaymentAndGrantsPointsExactlyOnce() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        Order order = pending();
        when(orders.findForUpdate(order.getOutTradeNo())).thenReturn(Optional.of(order));

        assertThat(service.handleNotify(notifyParams(order.getOutTradeNo(), Plan.MONTHLY.getTotalAmount()))).isEqualTo("success");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(points, times(1)).grantSubscription(USER, Plan.MONTHLY, order.getOutTradeNo());

        assertThat(service.handleNotify(notifyParams(order.getOutTradeNo(), Plan.MONTHLY.getTotalAmount()))).isEqualTo("success");
        verify(points, times(1)).grantSubscription(USER, Plan.MONTHLY, order.getOutTradeNo());
    }

    @Test
    void reportsFailureWhenTheOrderIsUnknown() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        when(orders.findForUpdate(any())).thenReturn(Optional.empty());

        assertThat(service.handleNotify(notifyParams("c".repeat(32), Plan.MONTHLY.getTotalAmount()))).isEqualTo("failure");
    }

    @Test
    void grantsPermanentPointsForATopUpInsteadOfASubscription() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        Order order = pendingTopUp();
        when(orders.findForUpdate(order.getOutTradeNo())).thenReturn(Optional.of(order));

        assertThat(service.handleNotify(notifyParams(order.getOutTradeNo(), Plan.TOPUP_50.getTotalAmount())))
                .isEqualTo("success");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(points, times(1)).grantPurchase(USER, Plan.TOPUP_50.getPeriodPoints(), order.getOutTradeNo());
        verify(points, never()).grantSubscription(anyLong(), any(), any());
    }

    @Test
    void topUpNotificationIsIdempotent() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        Order order = new Order(USER, Plan.TOPUP_100, Instant.now(), Instant.now().plusSeconds(7200));
        when(orders.findForUpdate(order.getOutTradeNo())).thenReturn(Optional.of(order));

        String params = Plan.TOPUP_100.getTotalAmount();
        assertThat(service.handleNotify(notifyParams(order.getOutTradeNo(), params))).isEqualTo("success");
        assertThat(service.handleNotify(notifyParams(order.getOutTradeNo(), params))).isEqualTo("success");

        verify(points, times(1)).grantPurchase(USER, Plan.TOPUP_100.getPeriodPoints(), order.getOutTradeNo());
    }

    @Test
    void rejectsATopUpNotificationWithTheWrongAmount() {
        when(alipay.verifyNotify(any())).thenReturn(true);
        Order order = pendingTopUp();
        when(orders.findForUpdate(order.getOutTradeNo())).thenReturn(Optional.of(order));

        assertThat(service.handleNotify(notifyParams(order.getOutTradeNo(), Plan.TOPUP_10.getTotalAmount())))
                .isEqualTo("failure");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(points, never()).grantPurchase(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    // ---- 主动查询 ----

    @Test
    void queryConfirmationMatchesNotificationConfirmation() {
        Order order = pending();
        when(orders.findByOutTradeNoAndUserId(order.getOutTradeNo(), USER)).thenReturn(Optional.of(order));
        when(alipay.query(order.getOutTradeNo())).thenReturn(
                new AlipayGateway.TradeQueryResult(true, "TRADE_SUCCESS", Plan.MONTHLY.getTotalAmount(), "2026092522001234567890"));
        when(orders.findForUpdate(order.getOutTradeNo())).thenReturn(Optional.of(order));

        PaymentDtos.OrderItem item = service.confirmByQuery(USER, order.getOutTradeNo());

        assertThat(item.status()).isEqualTo(OrderStatus.PAID);
        verify(points, times(1)).grantSubscription(USER, Plan.MONTHLY, order.getOutTradeNo());
    }

    @Test
    void queryConfirmationAlsoGrantsPermanentPointsForATopUp() {
        Order order = pendingTopUp();
        when(orders.findByOutTradeNoAndUserId(order.getOutTradeNo(), USER)).thenReturn(Optional.of(order));
        when(alipay.query(order.getOutTradeNo())).thenReturn(
                new AlipayGateway.TradeQueryResult(true, "TRADE_SUCCESS", Plan.TOPUP_50.getTotalAmount(), "2026092522001234567890"));
        when(orders.findForUpdate(order.getOutTradeNo())).thenReturn(Optional.of(order));

        PaymentDtos.OrderItem item = service.confirmByQuery(USER, order.getOutTradeNo());

        assertThat(item.status()).isEqualTo(OrderStatus.PAID);
        verify(points, times(1)).grantPurchase(USER, Plan.TOPUP_50.getPeriodPoints(), order.getOutTradeNo());
        verify(points, never()).grantSubscription(anyLong(), any(), any());
    }

    @Test
    void queryLeavesUnpaidOrdersAlone() {
        Order order = pending();
        when(orders.findByOutTradeNoAndUserId(order.getOutTradeNo(), USER)).thenReturn(Optional.of(order));
        when(alipay.query(order.getOutTradeNo())).thenReturn(AlipayGateway.TradeQueryResult.notFound());

        assertThat(service.confirmByQuery(USER, order.getOutTradeNo()).status()).isEqualTo(OrderStatus.PENDING);
        verify(points, never()).grantSubscription(anyLong(), any(), any());
    }

    // ---- 查询自己的订单 ----

    @Test
    void listsOnlyOwnOrders() {
        when(orders.findByUserIdOrderByCreatedAtDesc(eq(USER), any())).thenReturn(java.util.List.of(pending()));

        assertThat(service.list(USER, 20)).hasSize(1);
        verify(orders).findByUserIdOrderByCreatedAtDesc(eq(USER), any());
    }

    @Test
    void treatsSomeoneElsesOrderDetailAsMissing() {
        when(orders.findByOutTradeNoAndUserId(any(), eq(USER))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(USER, "d".repeat(32)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("订单不存在");
    }
}
