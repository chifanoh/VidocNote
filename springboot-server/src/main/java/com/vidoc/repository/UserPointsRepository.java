package com.vidoc.repository;

import com.vidoc.entity.UserPoints;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserPointsRepository extends JpaRepository<UserPoints, Long> {

    /** 锁定读：同一用户的扣分 / 退回在此串行化，避免丢失更新。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserPoints p where p.userId = :userId")
    Optional<UserPoints> findForUpdate(@Param("userId") Long userId);

    /**
     * 建行并发放注册赠送的永久积分；已存在则什么都不做。
     */
    @Modifying
    @Query(value = """
            insert ignore into user_points
                (user_id, subscription, purchase, total, updated_at)
            values (:userId, 0, :grant, :grant, :now)
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId, @Param("grant") int grant, @Param("now") Instant now);

    /** 给所有存量用户补一行，幂等；只为消掉「老用户首次访问才建行」的并发窗口。 */
    @Modifying
    @Query(value = """
            insert ignore into user_points
                (user_id, subscription, purchase, total, updated_at)
            select id, 0, 0, 0, :now from users
            """, nativeQuery = true)
    int backfillMissing(@Param("now") Instant now);
}
