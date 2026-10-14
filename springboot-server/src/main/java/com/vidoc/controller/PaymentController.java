package com.vidoc.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.vidoc.dto.PaymentDtos;
import com.vidoc.payment.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 订单与支付宝电脑网站支付。notify 是唯一匿名端点，安全边界只有验签。 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final int LIST_DEFAULT_SIZE = 20;

    private final OrderService orderService;

    public PaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public PaymentDtos.OrderItem create(@Valid @RequestBody PaymentDtos.CreateOrderRequest request) {
        return orderService.create(StpUtil.getLoginIdAsLong(), request.planCode());
    }

    @GetMapping("/orders")
    public List<PaymentDtos.OrderItem> list(
            @RequestParam(defaultValue = "" + LIST_DEFAULT_SIZE) int size) {
        return orderService.list(StpUtil.getLoginIdAsLong(), size);
    }

    @GetMapping("/orders/{outTradeNo}")
    public PaymentDtos.OrderItem detail(@PathVariable String outTradeNo) {
        return orderService.detail(StpUtil.getLoginIdAsLong(), outTradeNo);
    }

    /** 订阅是否生效中，用于前端置灰购买入口 */
    @GetMapping("/subscription")
    public PaymentDtos.SubscriptionResponse subscription() {
        return new PaymentDtos.SubscriptionResponse(orderService.subscriptionActive(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/orders/{outTradeNo}/pay")
    public PaymentDtos.PayFormResponse pay(@PathVariable String outTradeNo) {
        return orderService.pay(StpUtil.getLoginIdAsLong(), outTradeNo);
    }

    /** 从收银台返回后主动确认，兜异步通知丢失 */
    @PostMapping("/orders/{outTradeNo}/confirm")
    public PaymentDtos.OrderItem confirm(@PathVariable String outTradeNo) {
        return orderService.confirmByQuery(StpUtil.getLoginIdAsLong(), outTradeNo);
    }

    @PostMapping(value = "/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> params) {
        return orderService.handleNotify(params);
    }
}
