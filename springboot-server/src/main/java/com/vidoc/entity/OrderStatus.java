package com.vidoc.entity;

public enum OrderStatus {

    /** 已创建、待支付 */
    PENDING,

    /** 已支付，积分已发放 */
    PAID,

    /** 已关闭（超时未支付） */
    CLOSED
}
