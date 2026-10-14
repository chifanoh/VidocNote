package com.vidoc.entity;

/** 积分流水事由。对外只发这个 key，中文文案由前端映射。 */
public enum PointsReason {
    /** 生成摘要扣除 */
    CONSUME,
    /** 生成失败原额退回 */
    REFUND,
    /** 购买积分包到账（支付模块接缝，当前无调用方） */
    PURCHASE
}
