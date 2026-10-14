package com.vidoc.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 支付宝电脑网站支付订单。状态只能经 {@link #markPaid} / {@link #close} 跃迁，
 * 幂等由状态机保证：只有 PENDING 能被改写一次。
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_created", columnList = "user_id, created_at")
})
@Getter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /** 商户订单号：UUIDv7 去横线的 32 位十六进制，全局唯一且不可猜，直接对外标识订单 */
    @Column(name = "out_trade_no", nullable = false, length = 32, unique = true, updatable = false)
    @Setter(AccessLevel.NONE)
    private String outTradeNo;

    @Column(name = "user_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long userId;

    @Column(name = "plan_code", nullable = false, length = 32, updatable = false)
    @Setter(AccessLevel.NONE)
    private String planCode;

    /** 金额以分计，避免浮点 */
    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private int amount;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private int periods;

    @Column(name = "period_points", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private int periodPoints;

    @Column(name = "period_seconds", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private long periodSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "alipay_trade_no", length = 64)
    private String alipayTradeNo;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** 支付超时时刻，同时也是订单的关闭时刻（不做定时任务，惰性判定） */
    @Column(name = "expire_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant expireAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    protected Order() {
        // JPA
    }

    public Order(Long userId, Plan plan, Instant now, Instant expireAt) {
        this.outTradeNo = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        this.userId = userId;
        this.planCode = plan.getCode();
        this.amount = plan.getAmountFen();
        this.periods = plan.getPeriods();
        this.periodPoints = plan.getPeriodPoints();
        this.periodSeconds = plan.getPeriodSeconds();
        this.expireAt = expireAt;
        this.createdAt = now;
    }

    public boolean isPayable(Instant now) {
        return status == OrderStatus.PENDING && expireAt.isAfter(now);
    }

    /** 支付成功。只从 PENDING 跃迁一次，返回是否真的发生了跃迁。 */
    public boolean markPaid(String alipayTradeNo, Instant now) {
        if (status != OrderStatus.PENDING) {
            return false;
        }
        this.status = OrderStatus.PAID;
        this.alipayTradeNo = alipayTradeNo;
        this.paidAt = now;
        return true;
    }

    /** 关闭订单（超时未支付）。已支付的订单不会被关闭。 */
    public boolean close() {
        if (status != OrderStatus.PENDING) {
            return false;
        }
        this.status = OrderStatus.CLOSED;
        return true;
    }
}
