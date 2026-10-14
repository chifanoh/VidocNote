package com.vidoc.payment;

import com.vidoc.config.AlipayProperties;
import com.vidoc.dto.PaymentDtos;
import com.vidoc.entity.Order;
import com.vidoc.entity.OrderStatus;
import com.vidoc.entity.Plan;
import com.vidoc.entity.PlanKind;
import com.vidoc.exception.ApiException;
import com.vidoc.repository.OrderRepository;
import com.vidoc.service.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 订单与支付结果确认。订单状态的唯一有效跃迁在 {@link #confirmPaid} 里，
 * 它在行锁内二次核对后才发积分，因此异步通知重复投递与并发查询都不会重复发放。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int LIST_MAX_SIZE = 50;

    /** 支付宝交易成功的两种状态，TRADE_FINISHED 表示交易结束不可退款，同样算已支付 */
    private static final List<String> PAID_STATUSES = List.of("TRADE_SUCCESS", "TRADE_FINISHED");

    private final OrderRepository orders;
    private final PointsService points;
    private final AlipayGateway alipay;
    private final AlipayProperties properties;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orders, PointsService points, AlipayGateway alipay,
                        AlipayProperties properties, PlatformTransactionManager txManager) {
        this.orders = orders;
        this.points = points;
        this.alipay = alipay;
        this.properties = properties;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional
    public PaymentDtos.OrderItem create(Long userId, String planCode) {
        Plan plan = Plan.of(planCode);
        if (plan == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐不存在");
        }
        if (!alipay.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "支付功能暂未开放");
        }
        if (plan.getKind() == PlanKind.SUBSCRIPTION && points.hasActiveSubscription(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "当前订阅仍在有效期内，到期后可再次购买");
        }
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getOrderTimeoutMinutes() * 60L);
        return toItem(orders.save(new Order(userId, plan, now, expireAt)));
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.OrderItem> list(Long userId, int size) {
        int safeSize = Math.min(Math.max(size, 1), LIST_MAX_SIZE);
        return orders.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeSize))
                .stream().map(OrderService::toItem).toList();
    }

    @Transactional
    public PaymentDtos.OrderItem detail(Long userId, String outTradeNo) {
        return toItem(loadOwned(userId, outTradeNo));
    }

    /** 前端据此决定订阅按钮是否可点；事务由 PointsService 自己开（它会拿行锁并在必要时结算） */
    public boolean subscriptionActive(Long userId) {
        return points.hasActiveSubscription(userId);
    }

    /** 发起支付：先按超时惰性关闭，再把支付宝返回的自提交表单交给前端。出网调用不持有行锁。 */
    public PaymentDtos.PayFormResponse pay(Long userId, String outTradeNo) {
        Order order = loadOwned(userId, outTradeNo);
        Instant now = Instant.now();
        if (order.getStatus() == OrderStatus.PENDING && !order.getExpireAt().isAfter(now)) {
            closeExpired(outTradeNo);
            throw new ApiException(HttpStatus.CONFLICT, "订单已超时关闭，请重新下单");
        }
        if (!order.isPayable(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "订单已支付或已关闭");
        }
        return new PaymentDtos.PayFormResponse(order.getOutTradeNo(), alipay.pagePay(order));
    }

    /** 主动查询兜底：异步通知可能丢失，用户从收银台返回时用它把订单确认掉。 */
    public PaymentDtos.OrderItem confirmByQuery(Long userId, String outTradeNo) {
        Order order = loadOwned(userId, outTradeNo);
        if (order.getStatus() != OrderStatus.PENDING) {
            return toItem(order);
        }
        AlipayGateway.TradeQueryResult result = alipay.query(outTradeNo);
        if (!result.isPaid()) {
            return toItem(order);
        }
        if (!confirmPaid(outTradeNo, result.tradeNo(), result.totalAmount())) {
            log.warn("查询到已支付但确认失败，订单 {} 金额 {} 与本地不一致", outTradeNo, result.totalAmount());
        }
        return toItem(loadOwned(userId, outTradeNo));
    }

    /**
     * 处理异步通知，返回应答给支付宝的字面量。
     * 只有「确实不该重投」的情况才回 success —— 订单查不到或归属核对不过一律回 failure，
     * 借支付宝的重投窗口把问题暴露出来，而不是静默吞掉。
     */
    public String handleNotify(Map<String, String> params) {
        if (!alipay.verifyNotify(params)) {
            log.warn("支付宝通知验签失败，已拒绝。out_trade_no={}", params.get("out_trade_no"));
            return "failure";
        }
        String outTradeNo = params.get("out_trade_no");
        if (outTradeNo == null || outTradeNo.isBlank()) {
            log.warn("支付宝通知缺少 out_trade_no");
            return "failure";
        }
        if (!properties.getAppId().equals(params.get("app_id"))) {
            log.warn("支付宝通知的 app_id 与本地不符：{}", params.get("app_id"));
            return "failure";
        }
        String tradeStatus = params.get("trade_status");
        if (tradeStatus == null || !PAID_STATUSES.contains(tradeStatus)) {
            log.info("支付宝通知交易未成功（{}），订单 {} 保持原状态", tradeStatus, outTradeNo);
            return "success";
        }
        if (!confirmPaid(outTradeNo, params.get("trade_no"), params.get("total_amount"))) {
            log.warn("支付宝通知确认失败，订单 {} 金额 {}", outTradeNo, params.get("total_amount"));
            return "failure";
        }
        return "success";
    }

    /**
     * 行锁内确认支付并发积分：只有 PENDING 会被跃迁一次，
     * 重复通知与并发查询都在这里被挡掉。返回是否确认成功（已支付也算成功）。
     */
    boolean confirmPaid(String outTradeNo, String alipayTradeNo, String totalAmount) {
        Boolean confirmed = tx.execute(status -> {
            Order order = orders.findForUpdate(outTradeNo).orElse(null);
            if (order == null) {
                return false;
            }
            Plan plan = Plan.of(order.getPlanCode());
            if (plan == null || totalAmount == null || !plan.getTotalAmount().equals(totalAmount.trim())) {
                return false;
            }
            if (!order.markPaid(alipayTradeNo, Instant.now())) {
                // 已经是 PAID：说明是重复通知，直接算确认成功
                return order.getStatus() == OrderStatus.PAID;
            }
            orders.save(order);
            if (plan.getKind() == PlanKind.SUBSCRIPTION) {
                points.grantSubscription(order.getUserId(), plan, order.getOutTradeNo());
            } else {
                points.grantPurchase(order.getUserId(), plan.getPeriodPoints(), order.getOutTradeNo());
            }
            log.info("订单 {} 已确认支付，套餐 {}，支付宝交易号 {}", outTradeNo, plan.getCode(), alipayTradeNo);
            return true;
        });
        return Boolean.TRUE.equals(confirmed);
    }

    private void closeExpired(String outTradeNo) {
        orders.findByOutTradeNo(outTradeNo).ifPresent(order -> {
            if (order.close()) {
                orders.save(order);
            }
        });
    }

    private Order loadOwned(Long userId, String outTradeNo) {
        return orders.findByOutTradeNoAndUserId(outTradeNo, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private static PaymentDtos.OrderItem toItem(Order order) {
        Plan plan = Plan.of(order.getPlanCode());
        return new PaymentDtos.OrderItem(
                order.getOutTradeNo(),
                order.getPlanCode(),
                plan == null ? order.getPlanCode() : plan.getSubject(),
                order.getAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getPaidAt());
    }
}
