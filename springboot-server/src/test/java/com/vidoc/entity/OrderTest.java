package com.vidoc.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final Instant EXPIRE = NOW.plusSeconds(7200);
    private static final Long USER = 7L;

    private Order order() {
        return new Order(USER, Plan.MONTHLY, NOW, EXPIRE);
    }

    @Test
    void takesAmountAndQuotaFromTheServerSidePlan() {
        Order order = order();

        assertThat(order.getAmount()).isEqualTo(Plan.MONTHLY.getAmountFen());
        assertThat(order.getPeriods()).isEqualTo(Plan.MONTHLY.getPeriods());
        assertThat(order.getPeriodPoints()).isEqualTo(Plan.MONTHLY.getPeriodPoints());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void generatesDistinctMerchantOrderNumbers() {
        Order first = order();
        Order second = order();

        assertThat(first.getOutTradeNo()).hasSize(32).isNotEqualTo(second.getOutTradeNo());
        assertThat(first.getOutTradeNo()).matches("[0-9a-f]{32}");
    }

    @Test
    void paysOnlyOnce() {
        Order order = order();

        assertThat(order.markPaid("2026092522001", NOW)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        assertThat(order.getAlipayTradeNo()).isEqualTo("2026092522001");

        assertThat(order.markPaid("2026092522002", NOW)).isFalse();
        assertThat(order.getAlipayTradeNo()).isEqualTo("2026092522001");
    }

    @Test
    void refusesToCloseAPaidOrder() {
        Order order = order();
        order.markPaid("2026092522001", NOW);

        assertThat(order.close()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void closesOnlyOnce() {
        Order order = order();

        assertThat(order.close()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(order.close()).isFalse();
    }

    @Test
    void isPayableOnlyWhilePendingAndNotExpired() {
        Order order = order();

        assertThat(order.isPayable(NOW)).isTrue();
        assertThat(order.isPayable(EXPIRE)).isFalse();

        order.markPaid("2026092522001", NOW);
        assertThat(order.isPayable(NOW)).isFalse();
    }
}
