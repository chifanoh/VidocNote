package com.vidoc.repository;

import com.vidoc.dto.PointsDtos;
import com.vidoc.entity.PointsLedger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointsLedgerRepository extends JpaRepository<PointsLedger, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("""
            select new com.vidoc.dto.PointsDtos$PointsLedgerItem(
                p.createdAt, p.reason, p.delta, s.publicId)
            from PointsLedger p
            left join VideoSummary s on s.id = p.summaryId
            where p.userId = :userId
            order by p.id desc
            """)
    List<PointsDtos.PointsLedgerItem> findRecentItems(@Param("userId") Long userId, Pageable limit);

    @Modifying
    @Query(value = """
            delete from points_ledger
            where user_id = :userId
              and id < (select keep_id from (
                  select id as keep_id from points_ledger
                  where user_id = :userId order by id desc limit 1 offset :offset
              ) t)
            """, nativeQuery = true)
    int trim(@Param("userId") Long userId, @Param("offset") int offset);
}
