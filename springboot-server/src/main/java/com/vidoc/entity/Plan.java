package com.vidoc.entity;

/**
 * 在售套餐的唯一服务端定义：金额与额度只认这里，接口不接受客户端传入的价格。
 * 与前端 {@code app/utils/plans.ts} 的展示文案一一对应，改价要两边一起改。
 *
 * <p>两类商品共用一套订单与收单流程，差别只在确认支付后往哪个积分账别发放：
 * 订阅按期滴灌限时积分、生效期内不可再买；充值一次性发放永久积分、随时可买。
 * 充值档的 {@code periodPoints} 表示「一次性到账的积分数」，{@code periods} 固定为 1、
 * 周期长度为 0（不参与滴灌推进）。
 */
public enum Plan {

    MONTHLY_9(PlanKind.SUBSCRIPTION, "monthly_9", "Vidoc Note 入门月付", 990, 1, 1000, 30L * 86400),

    MONTHLY(PlanKind.SUBSCRIPTION, "monthly", "Vidoc Note 月付", 1990, 1, 2000, 30L * 86400),

    /** 12 期；12 × 365/12 天正好 365 天 */
    YEARLY(PlanKind.SUBSCRIPTION, "yearly", "Vidoc Note 年付", 18900, 12, 3000, 365L * 86400 / 12),

    TOPUP_10(PlanKind.TOPUP, "topup_10", "Vidoc Note 积分充值 500", 1000, 1, 500, 0),

    TOPUP_50(PlanKind.TOPUP, "topup_50", "Vidoc Note 积分充值 3000", 5000, 1, 3000, 0),

    TOPUP_100(PlanKind.TOPUP, "topup_100", "Vidoc Note 积分充值 7000", 10000, 1, 7000, 0);

    private final PlanKind kind;
    private final String code;
    private final String subject;
    private final int amountFen;
    private final int periods;
    private final int periodPoints;
    private final long periodSeconds;

    Plan(PlanKind kind, String code, String subject, int amountFen, int periods, int periodPoints, long periodSeconds) {
        this.kind = kind;
        this.code = code;
        this.subject = subject;
        this.amountFen = amountFen;
        this.periods = periods;
        this.periodPoints = periodPoints;
        this.periodSeconds = periodSeconds;
    }

    public PlanKind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public String getSubject() {
        return subject;
    }

    public int getAmountFen() {
        return amountFen;
    }

    public int getPeriods() {
        return periods;
    }

    public int getPeriodPoints() {
        return periodPoints;
    }

    public long getPeriodSeconds() {
        return periodSeconds;
    }

    /** 支付宝只认元的字符串，且必须两位小数。 */
    public String getTotalAmount() {
        return String.format("%d.%02d", amountFen / 100, amountFen % 100);
    }

    public static Plan of(String code) {
        for (Plan plan : values()) {
            if (plan.code.equals(code)) {
                return plan;
            }
        }
        return null;
    }
}
