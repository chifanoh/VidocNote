package com.vidoc.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPointsTest {

    private static final Long USER = 7L;
    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private UserPoints row(int purchase) {
        return new UserPoints(USER, purchase, NOW);
    }

    @Test
    void totalEqualsSumOfParts() {
        UserPoints p = row(100);
        p.grantSubscription(1, 2000, 86400, NOW.plusSeconds(86400));

        assertThat(p.getTotal()).isEqualTo(100 + 2000);
    }

    @Test
    void spendsSubscriptionThenPurchase() {
        UserPoints p = row(1000);
        p.grantSubscription(1, 100, 86400, NOW.plusSeconds(86400));

        UserPoints.Charge charge = p.spend(150);

        assertThat(charge).isEqualTo(new UserPoints.Charge(100, 50));
        assertThat(p.getSubscription()).isZero();
        assertThat(p.getPurchase()).isEqualTo(950);
        assertThat(p.getTotal()).isEqualTo(950);
    }

    @Test
    void spendsOnlyPermanentWhenSubscriptionIsEmpty() {
        UserPoints p = row(500);

        UserPoints.Charge charge = p.spend(20);

        assertThat(charge).isEqualTo(new UserPoints.Charge(0, 20));
        assertThat(p.getSubscription()).isZero();
        assertThat(p.getPurchase()).isEqualTo(480);
        assertThat(p.getTotal()).isEqualTo(480);
    }

    @Test
    void refusesToSpendMoreThanTotal() {
        UserPoints p = row(10);
        p.grantSubscription(1, 30, 86400, NOW.plusSeconds(86400));

        assertThatThrownBy(() -> p.spend(41))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺口");
    }

    @Test
    void restorePutsPointsBackIntoTheirOwnBuckets() {
        UserPoints p = row(100);
        p.grantSubscription(1, 100, 86400, NOW.plusSeconds(86400));

        UserPoints.Charge charge = p.spend(150);
        assertThat(p.getTotal()).isEqualTo(50);

        p.restore(charge);

        assertThat(p.getSubscription()).isEqualTo(100);
        assertThat(p.getPurchase()).isEqualTo(100);
        assertThat(p.getTotal()).isEqualTo(200);
    }

    @Test
    void expireSubscriptionClearsOnlyAfterTheDeadline() {
        UserPoints p = row(0);
        p.grantSubscription(1, 2000, 3600, NOW.plusSeconds(3600));

        assertThat(p.expireSubscription(NOW)).isFalse();
        assertThat(p.getSubscription()).isEqualTo(2000);

        assertThat(p.expireSubscription(NOW.plusSeconds(3600))).isTrue();
        assertThat(p.getSubscription()).isZero();
        assertThat(p.getTotal()).isZero();
    }

    @Test
    void expireSubscriptionIgnoresRowsWithoutDeadline() {
        UserPoints p = row(100);

        assertThat(p.expireSubscription(NOW.plusSeconds(99999))).isFalse();
        assertThat(p.getPurchase()).isEqualTo(100);
    }

    @Test
    void subscriptionAdvancesOnePeriodAtATime() {
        long period = 30L * 86400;
        UserPoints p = row(0);
        p.grantSubscription(2, 2000, period, NOW.plusSeconds(period));

        assertThat(p.getSubscription()).isEqualTo(2000);
        assertThat(p.getSubscriptionPeriodsLeft()).isEqualTo(1);

        assertThat(p.expireSubscription(NOW.plusSeconds(period))).isTrue();
        assertThat(p.getSubscription()).isEqualTo(2000);
        assertThat(p.getSubscriptionPeriodsLeft()).isZero();
        assertThat(p.getSubscriptionExpiresAt()).isEqualTo(NOW.plusSeconds(2 * period));

        assertThat(p.expireSubscription(NOW.plusSeconds(2 * period))).isTrue();
        assertThat(p.getSubscription()).isZero();
        assertThat(p.getSubscriptionExpiresAt()).isNull();
        assertThat(p.getTotal()).isZero();
    }

    @Test
    void subscriptionDoesNotBackfillMissedPeriods() {
        long period = 30L * 86400;
        UserPoints p = row(0);
        p.grantSubscription(12, 3000, period, NOW.plusSeconds(period));

        assertThat(p.expireSubscription(NOW.plusSeconds(3 * period + 1))).isTrue();

        assertThat(p.getSubscription()).isEqualTo(3000);
        assertThat(p.getSubscriptionPeriodsLeft()).isEqualTo(8);
        assertThat(p.getSubscriptionExpiresAt()).isEqualTo(NOW.plusSeconds(4 * period));
    }

    /** 永久积分不随订阅过期一起清零 */
    @Test
    void permanentPointsSurviveSubscriptionExpiry() {
        long period = 30L * 86400;
        UserPoints p = row(100);
        p.grantSubscription(1, 2000, period, NOW.plusSeconds(period));

        p.expireSubscription(NOW.plusSeconds(period + 1));

        assertThat(p.getSubscription()).isZero();
        assertThat(p.getPurchase()).isEqualTo(100);
        assertThat(p.getTotal()).isEqualTo(100);
    }
}
