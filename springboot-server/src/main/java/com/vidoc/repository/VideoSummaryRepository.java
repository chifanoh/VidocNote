package com.vidoc.repository;

import com.vidoc.dto.SummaryDtos;
import com.vidoc.entity.SummaryStatus;
import com.vidoc.entity.VideoSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoSummaryRepository extends JpaRepository<VideoSummary, Long> {

    /**
     * 历史列表首页（游标为空时走这条）：只取完成态
     */
    @Query("""
            select new com.vidoc.dto.SummaryDtos$SummaryListItem(
                s.publicId, s.videoUrl, s.title, s.author, s.thumbnailUrl, s.duration, s.createdAt)
            from VideoSummary s
            where s.userId = :userId and s.status = :status
            order by s.createdAt desc, s.publicId desc
            """)
    List<SummaryDtos.SummaryListItem> findListPage(@Param("userId") Long userId,
                                                   @Param("status") SummaryStatus status,
                                                   Pageable limit);

    /**
     * 历史列表翻页：只取游标之后的记录，谓词与排序键 (created_at, public_id) 对齐，
     * 配合索引 (user_id, status, created_at, public_id) 走范围扫描，深翻不退化。
     */
    @Query("""
            select new com.vidoc.dto.SummaryDtos$SummaryListItem(
                s.publicId, s.videoUrl, s.title, s.author, s.thumbnailUrl, s.duration, s.createdAt)
            from VideoSummary s
            where s.userId = :userId and s.status = :status
              and (s.createdAt < :beforeAt or (s.createdAt = :beforeAt and s.publicId < :beforeId))
            order by s.createdAt desc, s.publicId desc
            """)
    List<SummaryDtos.SummaryListItem> findListPageAfter(@Param("userId") Long userId,
                                                        @Param("status") SummaryStatus status,
                                                        @Param("beforeAt") Instant beforeAt,
                                                        @Param("beforeId") String beforeId,
                                                        Pageable limit);

    /** 详情 / 删除按对外标识查，顺带校验归属：不是自己的记录查不到，与「不存在」同一条路径。 */
    Optional<VideoSummary> findByPublicIdAndUserId(String publicId, Long userId);

    /**
     * 删除进程重启后无人处理的遗留任务（调用点见 {@code SummaryStartupCleaner}），返回删除行数。
     * {@code before} 传本进程的启动时刻，只动比它更早的行。
     */
    @Modifying
    @Transactional
    @Query("delete from VideoSummary s where s.status in :statuses and s.createdAt < :before")
    int deleteUnfinishedBefore(@Param("statuses") Collection<SummaryStatus> statuses,
                               @Param("before") Instant before);
}
