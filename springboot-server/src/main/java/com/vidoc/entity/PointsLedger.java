package com.vidoc.entity;

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
 * 积分流水（不记每日赠送）：只作展示与追溯用（余额真源是 {@link UserPoints}），每个用户只保留最近若干条
 */
@Entity
@Table(name = "points_ledger", indexes = {
        @Index(name = "idx_points_ledger_user", columnList = "user_id, id")
})
@Getter
@Setter
public class PointsLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long userId;

    /** 正数为发放 / 退回，负数为消耗 */
    @Column(nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointsReason reason;

    /** 消耗 / 退回关联的摘要主键，仅服务端内部使用，不出接口 */
    @Column(name = "summary_id")
    private Long summaryId;

    /** 幂等键，唯一索引是防重复扣分 / 重复退回的最后一道闸 */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    @Setter(AccessLevel.NONE)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt = Instant.now();

    protected PointsLedger() {
        // JPA
    }

    public PointsLedger(Long userId, int delta, PointsReason reason, Long summaryId,
                        String idempotencyKey, Instant createdAt) {
        this.userId = userId;
        this.delta = delta;
        this.reason = reason;
        this.summaryId = summaryId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }
}
