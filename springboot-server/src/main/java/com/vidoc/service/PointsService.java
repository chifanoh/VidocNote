package com.vidoc.service;

import com.vidoc.config.PointsProperties;
import com.vidoc.dto.PointsDtos;
import com.vidoc.entity.PointsLedger;
import com.vidoc.entity.PointsReason;
import com.vidoc.entity.Plan;
import com.vidoc.entity.UserPoints;
import com.vidoc.repository.PointsLedgerRepository;
import com.vidoc.repository.UserPointsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 积分：余额物化在 {@link UserPoints}，流水只作展示与追溯。
 * 扣分与退回都在「用户行锁」下进行，同一用户的并发操作在此串行化；每次操作都带唯一幂等键。
 * 订阅额度惰性清零，不依赖定时任务。
 */
@Service
public class PointsService {

    private static final Logger log = LoggerFactory.getLogger(PointsService.class);

    /** 单条摘要的最低扣分（短视频起步价） */
    public static final int MIN_COST = 10;

    private final UserPointsRepository userPoints;
    private final PointsLedgerRepository ledger;
    private final int signupGrant;
    private final int ledgerKeep;

    public PointsService(UserPointsRepository userPoints, PointsLedgerRepository ledger,
                         PointsProperties properties) {
        this.userPoints = userPoints;
        this.ledger = ledger;
        this.signupGrant = properties.getSignupGrant();
        this.ledgerKeep = properties.getLedgerKeep();
        log.info("积分：注册赠送 {} 永久积分，流水每用户保留最近 {} 条", signupGrant, ledgerKeep);
    }

    /** 按视频时长算分：阶梯只作用于超出部分，档位之间连续（60 分钟 60 分、61 分钟 62 分）。 */
    public static int costOf(Long durationSeconds) {
        long minutes = durationSeconds == null || durationSeconds <= 0 ? 0 : (durationSeconds + 59) / 60;
        if (minutes <= 10) {
            return MIN_COST;
        }
        if (minutes <= 60) {
            return (int) minutes;
        }
        if (minutes <= 180) {
            return (int) Math.ceil(60 + (minutes - 60) * 1.5);
        }
        return (int) Math.ceil(240 + (minutes - 180) * 2.0);
    }

    /** 可用积分（合计）。 */
    @Transactional
    public int balance(Long userId) {
        return current(userId).getTotal();
    }

    /**
     * 取该用户的积分行：订阅过期时惰性结算。
     * <p>正常情况下只是一次普通的单行读；只有确实要结算时才加行锁。
     */
    private UserPoints current(Long userId) {
        Instant now = Instant.now();
        Optional<UserPoints> found = userPoints.findById(userId);
        if (found.isPresent() && !isStale(found.get(), now)) {
            return found.get();
        }
        UserPoints row = lock(userId);
        refresh(row, now);
        return row;
    }

    /**
     * 扣分：返回各分项实际扣掉的构成（退回要按它原分项归还），余额不足则一分不扣并返回 {@code null}。
     * 幂等检查放在拿到行锁之后 —— 同一用户的并发操作在这里已被串行化，锁后读到的才是最终状态。
     */
    @Transactional
    public UserPoints.Charge consume(Long userId, int points, Long summaryId) {
        if (points <= 0) {
            return UserPoints.Charge.NONE;
        }
        String key = "CONSUME:" + summaryId;
        UserPoints row = lock(userId);
        if (ledger.existsByIdempotencyKey(key)) {
            // 同一摘要重复扣费：分项构成只有第一次扣费时才知道，这里给不出，也就退不回来（当前无重放路径）
            return UserPoints.Charge.NONE;
        }
        Instant now = Instant.now();
        refresh(row, now);
        if (row.getTotal() < points) {
            return null;
        }
        UserPoints.Charge charge = row.spend(points);
        row.setUpdatedAt(now);
        userPoints.save(row);
        writeLedger(userId, -points, PointsReason.CONSUME, summaryId, key, now);
        return charge;
    }

    /** 按扣费时的分项原样归还；空构成直接忽略。按摘要幂等。 */
    @Transactional
    public void refund(Long userId, UserPoints.Charge charge, Long summaryId) {
        if (charge == null || charge.isEmpty()) {
            return;
        }
        String key = "REFUND:" + summaryId;
        UserPoints row = lock(userId);
        if (ledger.existsByIdempotencyKey(key)) {
            return;
        }
        Instant now = Instant.now();
        refresh(row, now);
        row.restore(charge);
        row.setUpdatedAt(now);
        userPoints.save(row);
        writeLedger(userId, charge.total(), PointsReason.REFUND, summaryId, key, now);
    }

    /** 支付模块的接缝之一：充值到账，永久有效。按订单号幂等。 */
    @Transactional
    public void grantPurchase(Long userId, int points, String orderId) {
        if (points <= 0) {
            return;
        }
        String key = "ORDER:" + orderId;
        UserPoints row = lock(userId);
        if (ledger.existsByIdempotencyKey(key)) {
            return;
        }
        Instant now = Instant.now();
        refresh(row, now);
        row.grantPurchase(points);
        row.setUpdatedAt(now);
        userPoints.save(row);
        writeLedger(userId, points, PointsReason.PURCHASE, null, key, now);
    }

    /** 按订单发放订阅：首期立即到账，其余期数留在余额行里按期推进。按订单号幂等。 */
    @Transactional
    public void grantSubscription(Long userId, Plan plan, String orderId) {
        String key = "ORDER:" + orderId;
        UserPoints row = lock(userId);
        if (ledger.existsByIdempotencyKey(key)) {
            return;
        }
        Instant now = Instant.now();
        refresh(row, now);
        row.grantSubscription(plan.getPeriods(), plan.getPeriodPoints(), plan.getPeriodSeconds(),
                now.plusSeconds(plan.getPeriodSeconds()));
        row.setUpdatedAt(now);
        userPoints.save(row);
        writeLedger(userId, plan.getPeriodPoints(), PointsReason.PURCHASE, null, key, now);
    }

    /** 订阅是否仍在有效期内。下单前用它挡住「有效期内再买」——否则新订阅会覆盖并吞掉已付时长。 */
    @Transactional
    public boolean hasActiveSubscription(Long userId) {
        Instant now = Instant.now();
        UserPoints row = lock(userId);
        refresh(row, now);
        Instant expiresAt = row.getSubscriptionExpiresAt();
        return expiresAt != null && expiresAt.isAfter(now);
    }

    /** 最近流水（只读，不触发任何结算）。 */
    @Transactional(readOnly = true)
    public List<PointsDtos.PointsLedgerItem> recent(Long userId, int size) {
        return ledger.findRecentItems(userId, PageRequest.of(0, size));
    }

    /** 给用户建积分行（含注册赠送），幂等。注册时与启动回填调用，保证「每个用户都有一行」这个前提成立。 */
    @Transactional
    public void provision(Long userId) {
        userPoints.insertIfAbsent(userId, signupGrant, Instant.now());
    }

    /** 给存量用户补积分行，幂等；放到启动时跑。 */
    @Transactional
    public int backfillMissingRows() {
        return userPoints.backfillMissing(Instant.now());
    }

    private boolean isStale(UserPoints row, Instant now) {
        Instant expiresAt = row.getSubscriptionExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * 锁定读用户积分行。正常路径只有一条 {@code SELECT ... FOR UPDATE}：行在注册时、存量用户在启动回填时
     * 就已经建好，同一用户的并发操作全在这里串行化。
     * 补建只是兜底 —— 并发首次访问会走到它，而「间隙锁 + 插入」在极端情况下会死锁，所以不该让它成为常态。
     */
    private UserPoints lock(Long userId) {
        Optional<UserPoints> found = userPoints.findForUpdate(userId);
        if (found.isPresent()) {
            return found.get();
        }
        log.warn("用户 {} 缺少积分行，兜底补建", userId);
        provision(userId);
        return userPoints.findForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("user_points 行补建失败，userId=" + userId));
    }

    /** 订阅过期清理；有变动才落库。 */
    private void refresh(UserPoints row, Instant now) {
        if (row.expireSubscription(now)) {
            row.setUpdatedAt(now);
            userPoints.save(row);
        }
    }

    private void writeLedger(Long userId, int delta, PointsReason reason, Long summaryId,
                             String key, Instant now) {
        // 先落库再裁剪：裁剪的子查询要看得见这一行，否则会多删一条
        ledger.saveAndFlush(new PointsLedger(userId, delta, reason, summaryId, key, now));
        ledger.trim(userId, Math.max(0, ledgerKeep - 1));
    }
}
