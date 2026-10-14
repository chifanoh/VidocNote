package com.vidoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户积分余额（一户一行，唯一真源）。
 * 只有两个来源：月付额度按 {@code subscriptionExpiresAt} 惰性清零，永久积分（注册赠送 / 充值）不过期。
 */
@Entity
@Table(name = "user_points")
@Getter
@Setter
public class UserPoints {

    @Id
    @Column(name = "user_id")
    @Setter(AccessLevel.NONE)
    private Long userId;

    /** 月付积分，当期有效 */
    @Setter(AccessLevel.NONE)
    private int subscription;

    /** 永久积分，不过期（注册赠送与充值都进这里） */
    @Setter(AccessLevel.NONE)
    private int purchase;

    /** 合计，永远等于两个分项之和；只能由分项变动时重算，不开放 setter 以免漂移 */
    @Setter(AccessLevel.NONE)
    private int total;

    @Column(name = "subscription_expires_at")
    @Setter(AccessLevel.NONE)
    private Instant subscriptionExpiresAt;

    /** 当前订阅每期的额度 */
    @Column(name = "subscription_period_points", nullable = false)
    @Setter(AccessLevel.NONE)
    private int subscriptionPeriodPoints;

    /** 除当前期之外还剩几期没到期 */
    @Column(name = "subscription_periods_left", nullable = false)
    @Setter(AccessLevel.NONE)
    private int subscriptionPeriodsLeft;

    /** 每期时长（秒）。月付与年付不同，故随订阅一起物化，避免过期结算时反查订单。 */
    @Column(name = "subscription_period_seconds", nullable = false)
    @Setter(AccessLevel.NONE)
    private long subscriptionPeriodSeconds;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPoints() {
        // JPA
    }

    public UserPoints(Long userId, int purchase, Instant now) {
        this.userId = userId;
        this.purchase = purchase;
        this.updatedAt = now;
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.total = subscription + purchase;
    }

    /**
     * 一次扣费在各分项的构成。退回只认它 —— 只记总额会把订阅的限时积分退成永久积分。
     */
    public record Charge(int subscription, int purchase) {

        public static final Charge NONE = new Charge(0, 0);

        public int total() {
            return subscription + purchase;
        }

        public boolean isEmpty() {
            return total() == 0;
        }
    }

    /** 依次从月付 → 永久扣，返回各分项实际扣掉的分数；调用方须先用 {@link #getTotal()} 保证够扣。 */
    public Charge spend(int points) {
        int remaining = points;

        int fromSubscription = Math.min(subscription, remaining);
        subscription -= fromSubscription;
        remaining -= fromSubscription;

        int fromPurchase = Math.min(purchase, remaining);
        purchase -= fromPurchase;
        remaining -= fromPurchase;

        if (remaining > 0) {
            throw new IllegalStateException("积分余额不足以扣除 " + points + " 分，缺口 " + remaining);
        }
        recalculateTotal();
        return new Charge(fromSubscription, fromPurchase);
    }

    /** 退回按扣费时的构成放回原分项：订阅积分仍是限时，不会洗成永久积分。 */
    public void restore(Charge charge) {
        subscription += charge.subscription();
        purchase += charge.purchase();
        recalculateTotal();
    }

    /** 永久积分到账（注册赠送 / 充值 / 退回）。 */
    public void grantPurchase(int points) {
        purchase += points;
        recalculateTotal();
    }

    /** 订阅按期发放：首期立即到账，其余期数留待后续按期推进。 */
    public void grantSubscription(int periods, int periodPoints, long periodSeconds, Instant firstPeriodEndsAt) {
        subscription = periodPoints;
        subscriptionPeriodPoints = periodPoints;
        subscriptionPeriodsLeft = Math.max(0, periods - 1);
        subscriptionPeriodSeconds = periodSeconds;
        subscriptionExpiresAt = firstPeriodEndsAt;
        recalculateTotal();
    }

    /**
     * 订阅到期按期推进：一次跨过多期时只发当前所处那一期，错过的期数不补发；期数发完则清零。
     * 返回是否有变动。
     */
    public boolean expireSubscription(Instant now) {
        if (subscriptionExpiresAt == null || subscriptionExpiresAt.isAfter(now)) {
            return false;
        }
        while (subscriptionPeriodsLeft > 0 && subscriptionPeriodSeconds > 0
                && !subscriptionExpiresAt.isAfter(now)) {
            subscriptionPeriodsLeft--;
            subscriptionExpiresAt = subscriptionExpiresAt.plusSeconds(subscriptionPeriodSeconds);
        }
        if (subscriptionExpiresAt.isAfter(now)) {
            subscription = subscriptionPeriodPoints;
        } else {
            subscription = 0;
            subscriptionPeriodPoints = 0;
            subscriptionPeriodSeconds = 0;
            subscriptionPeriodsLeft = 0;
            subscriptionExpiresAt = null;
        }
        recalculateTotal();
        return true;
    }
}
