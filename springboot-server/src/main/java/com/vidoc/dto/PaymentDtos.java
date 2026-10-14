package com.vidoc.dto;

import com.vidoc.entity.OrderStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** 只接受套餐标识；金额与额度一律取服务端定义，多传的字段被忽略 */
    public record CreateOrderRequest(@NotBlank String planCode) {
    }

    public record OrderItem(
            String outTradeNo,
            String planCode,
            String planTitle,
            int amount,
            OrderStatus status,
            Instant createdAt,
            Instant paidAt) {
    }

    /** 自动提交的 form HTML，由前端写入新窗口完成跳转 */
    public record PayFormResponse(String outTradeNo, String formHtml) {
    }

    /** 只回一个布尔：有没有生效中的订阅。额度、剩余期数、到期时刻都不出接口 */
    public record SubscriptionResponse(boolean active) {
    }
}
