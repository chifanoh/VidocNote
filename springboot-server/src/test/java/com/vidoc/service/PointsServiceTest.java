package com.vidoc.service;

import com.vidoc.config.PointsProperties;
import com.vidoc.entity.PointsLedger;
import com.vidoc.entity.PointsReason;
import com.vidoc.entity.Plan;
import com.vidoc.entity.UserPoints;
import com.vidoc.repository.PointsLedgerRepository;
import com.vidoc.repository.UserPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointsServiceTest {

    private static final Long USER = 7L;
    private static final Long SUMMARY = 42L;

    private UserPointsRepository userPoints;
    private PointsLedgerRepository ledger;
    private PointsProperties properties;
    private PointsService service;

    @BeforeEach
    void setUp() {
        userPoints = mock(UserPointsRepository.class);
        ledger = mock(PointsLedgerRepository.class);
        properties = new PointsProperties();
        service = new PointsService(userPoints, ledger, properties);
    }

    private UserPoints existing(int purchase) {
        UserPoints row = new UserPoints(USER, purchase, Instant.now());
        when(userPoints.findForUpdate(USER)).thenReturn(Optional.of(row));
        return row;
    }

    private PointsLedger capturedLedger(int expectedSaves) {
        ArgumentCaptor<PointsLedger> captor = ArgumentCaptor.forClass(PointsLedger.class);
        verify(ledger, times(expectedSaves)).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ---- 扣分 ----

    @Test
    void consumeDeductsAndWritesLedger() {
        UserPoints row = existing(30);
        when(ledger.existsByIdempotencyKey("CONSUME:" + SUMMARY)).thenReturn(false);

        assertThat(service.consume(USER, 10, SUMMARY)).isEqualTo(new UserPoints.Charge(0, 10));

        assertThat(row.getPurchase()).isEqualTo(20);
        assertThat(row.getTotal()).isEqualTo(20);
        verify(userPoints).save(row);

        PointsLedger entry = capturedLedger(1);
        assertThat(entry.getDelta()).isEqualTo(-10);
        assertThat(entry.getReason()).isEqualTo(PointsReason.CONSUME);
        assertThat(entry.getSummaryId()).isEqualTo(SUMMARY);
        assertThat(entry.getIdempotencyKey()).isEqualTo("CONSUME:" + SUMMARY);
        // 裁剪到 keep 条：offset 是 keep - 1
        verify(ledger).trim(USER, properties.getLedgerKeep() - 1);
    }

    @Test
    void consumeChargesNothingWhenBalanceIsShort() {
        UserPoints row = existing(9);
        when(ledger.existsByIdempotencyKey(any())).thenReturn(false);

        assertThat(service.consume(USER, 30, SUMMARY)).isNull();

        assertThat(row.getTotal()).isEqualTo(9);
        verify(userPoints, never()).save(any());
        verify(ledger, never()).saveAndFlush(any());
        verify(ledger, never()).trim(anyLong(), anyInt());
    }

    @Test
    void consumeIsIdempotentPerSummary() {
        existing(30);
        when(ledger.existsByIdempotencyKey("CONSUME:" + SUMMARY)).thenReturn(true);

        assertThat(service.consume(USER, 30, SUMMARY)).isEqualTo(UserPoints.Charge.NONE);

        verify(userPoints, never()).save(any());
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void consumeSettlesTheSubscriptionBeforeCheckingBalance() {
        // 行上的订阅已经过了到期时刻 —— 扣分前必须先结算，否则会拿过期额度算余额
        UserPoints row = new UserPoints(USER, 0, Instant.now());
        row.grantSubscription(1, 2000, 3600, Instant.now().minusSeconds(1));
        when(userPoints.findForUpdate(USER)).thenReturn(Optional.of(row));
        when(ledger.existsByIdempotencyKey(any())).thenReturn(false);

        assertThat(service.consume(USER, 30, SUMMARY)).isNull();

        assertThat(row.getSubscription()).isZero();
        assertThat(row.getTotal()).isZero();
    }

    // ---- 退回 ----

    @Test
    void refundRestoresPointsToTheirOwnBuckets() {
        UserPoints row = new UserPoints(USER, 0, Instant.now());
        row.grantSubscription(1, 100, 2_592_000L, Instant.now().plusSeconds(3600));
        when(userPoints.findForUpdate(USER)).thenReturn(Optional.of(row));
        when(ledger.existsByIdempotencyKey("REFUND:" + SUMMARY)).thenReturn(false);

        service.refund(USER, new UserPoints.Charge(25, 5), SUMMARY);

        assertThat(row.getSubscription()).isEqualTo(125);
        assertThat(row.getPurchase()).isEqualTo(5);
        assertThat(row.getTotal()).isEqualTo(130);

        PointsLedger entry = capturedLedger(1);
        assertThat(entry.getDelta()).isEqualTo(30);
        assertThat(entry.getReason()).isEqualTo(PointsReason.REFUND);
        assertThat(entry.getSummaryId()).isEqualTo(SUMMARY);
    }

    @Test
    void refundIsIdempotentPerSummary() {
        existing(0);
        when(ledger.existsByIdempotencyKey("REFUND:" + SUMMARY)).thenReturn(true);

        service.refund(USER, new UserPoints.Charge(0, 30), SUMMARY);

        verify(userPoints, never()).save(any());
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void refundIgnoresEmptyCharge() {
        service.refund(USER, UserPoints.Charge.NONE, SUMMARY);

        verify(userPoints, never()).findForUpdate(anyLong());
        verify(ledger, never()).saveAndFlush(any());
    }

    // ---- 注册赠送 ----

    @Test
    void provisionGrantsTheSignupPoints() {
        service.provision(USER);

        verify(userPoints).insertIfAbsent(eq(USER), eq(properties.getSignupGrant()), any());
    }

    // ---- 购买（支付接缝） ----

    @Test
    void purchaseIsIdempotentPerOrder() {
        UserPoints row = existing(0);
        when(ledger.existsByIdempotencyKey("ORDER:o-1")).thenReturn(false);

        service.grantPurchase(USER, 1000, "o-1");

        assertThat(row.getPurchase()).isEqualTo(1000);
        PointsLedger entry = capturedLedger(1);
        assertThat(entry.getDelta()).isEqualTo(1000);
        assertThat(entry.getReason()).isEqualTo(PointsReason.PURCHASE);
        assertThat(entry.getSummaryId()).isNull();
    }

    @Test
    void purchaseIsSkippedForKnownOrder() {
        existing(0);
        when(ledger.existsByIdempotencyKey("ORDER:o-1")).thenReturn(true);

        service.grantPurchase(USER, 1000, "o-1");

        verify(userPoints, never()).save(any());
        verify(ledger, never()).saveAndFlush(any());
    }

    // ---- 余额 ----

    @Test
    void balanceReadsWithoutWritingWhenRowIsFresh() {
        UserPoints row = new UserPoints(USER, 20, Instant.now());
        when(userPoints.findById(USER)).thenReturn(Optional.of(row));

        assertThat(service.balance(USER)).isEqualTo(20);

        verify(userPoints, never()).findForUpdate(anyLong());
        verify(userPoints, never()).save(any());
        assertThat(row.getPurchase()).isEqualTo(20);
    }

    @Test
    void balanceSettlesAnExpiredSubscription() {
        UserPoints row = new UserPoints(USER, 100, Instant.now());
        row.grantSubscription(1, 2000, 3600, Instant.now().minusSeconds(1));
        when(userPoints.findById(USER)).thenReturn(Optional.of(row));
        when(userPoints.findForUpdate(USER)).thenReturn(Optional.of(row));

        assertThat(service.balance(USER)).isEqualTo(100);

        assertThat(row.getSubscription()).isZero();
        assertThat(row.getTotal()).isEqualTo(100);
    }

    @Test
    void balanceSkipsMissingRowThroughTheFastPath() {
        when(userPoints.findById(USER)).thenReturn(Optional.empty());
        when(userPoints.findForUpdate(USER)).thenReturn(Optional.of(new UserPoints(USER, 30, Instant.now())));

        assertThat(service.balance(USER)).isEqualTo(30);
    }

    // ---- 订阅（支付接入） ----

    @Test
    void grantSubscriptionCreditsOnlyTheFirstPeriod() {
        UserPoints row = existing(0);
        when(ledger.existsByIdempotencyKey("ORDER:o1")).thenReturn(false);

        service.grantSubscription(USER, Plan.YEARLY, "o1");

        assertThat(row.getSubscription()).isEqualTo(Plan.YEARLY.getPeriodPoints());
        assertThat(row.getSubscriptionPeriodsLeft()).isEqualTo(Plan.YEARLY.getPeriods() - 1);
        assertThat(row.getSubscriptionPeriodSeconds()).isEqualTo(Plan.YEARLY.getPeriodSeconds());
        assertThat(row.getTotal()).isEqualTo(Plan.YEARLY.getPeriodPoints());
        verify(userPoints).save(row);

        PointsLedger entry = capturedLedger(1);
        assertThat(entry.getReason()).isEqualTo(PointsReason.PURCHASE);
        assertThat(entry.getDelta()).isEqualTo(Plan.YEARLY.getPeriodPoints());
    }

    @Test
    void grantSubscriptionIsIdempotentPerOrder() {
        existing(0);
        when(ledger.existsByIdempotencyKey("ORDER:o1")).thenReturn(true);

        service.grantSubscription(USER, Plan.YEARLY, "o1");

        verify(userPoints, never()).save(any());
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void hasActiveSubscriptionFollowsTheDeadline() {
        UserPoints row = existing(0);
        assertThat(service.hasActiveSubscription(USER)).isFalse();

        row.grantSubscription(1, 2000, 86400, Instant.now().plusSeconds(3600));
        assertThat(service.hasActiveSubscription(USER)).isTrue();

        row.grantSubscription(1, 2000, 86400, Instant.now().minusSeconds(1));
        assertThat(service.hasActiveSubscription(USER)).isFalse();
    }
}
