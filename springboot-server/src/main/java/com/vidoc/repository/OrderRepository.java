package com.vidoc.repository;

import com.vidoc.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOutTradeNo(String outTradeNo);

    /** 通知处理要在行锁内读，否则同一笔交易的并发通知会双双看到 PENDING。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.outTradeNo = :outTradeNo")
    Optional<Order> findForUpdate(@Param("outTradeNo") String outTradeNo);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByOutTradeNoAndUserId(String outTradeNo, Long userId);
}
