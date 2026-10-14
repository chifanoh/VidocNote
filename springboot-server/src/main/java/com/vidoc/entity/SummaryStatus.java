package com.vidoc.entity;

/**
 * 摘要任务状态：PENDING → PROCESSING → COMPLETED。
 *
 * <p>{@link #FAILED} 已不再写出（失败的任务直接删除记录，见 {@code SummaryProcessor}），
 * 保留该值只为兼容库里 2026-09-11 之前的历史行与前端类型。
 */
public enum SummaryStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
