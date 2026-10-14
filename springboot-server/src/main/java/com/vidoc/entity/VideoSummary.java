package com.vidoc.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "video_summaries", indexes = {
        @Index(name = "idx_video_summaries_user_created", columnList = "user_id, created_at"),
        // 历史列表的 (user_id, status) 等值 + (created_at, public_id) 倒序：让 status 也走索引，
        // 且排序由索引序给出（无需 filesort）。前一个索引是它的前缀，冗余但 ddl-auto 不会删。
        @Index(name = "idx_video_summaries_user_status_created",
                columnList = "user_id, status, created_at, public_id")
})
@Getter
@Setter
public class VideoSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /**
     * 对外标识（UUIDv7）：URL 与接口只认它，内部主键不出现在响应里。
     * 不声明 NOT NULL：唯一索引与列都是这个版本才补的，非空由构造器保证。
     */
    @Column(name = "public_id", length = 36, unique = true, updatable = false)
    @Setter(AccessLevel.NONE)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long userId;

    @Column(name = "video_url", nullable = false, length = 1024)
    @Setter(AccessLevel.NONE)
    private String videoUrl;

    @Column(length = 512)
    private String title;

    @Column(length = 255)
    private String author;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;

    private Long duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SummaryStatus status = SummaryStatus.PENDING;

    /** 处理中的步骤标识（key，如 SUMMARIZE；展示文案由前端按 key 映射），终态时为 null。 */
    @Column(name = "progress_step", length = 64)
    private String progressStep;

    @Lob
    @Column(name = "summary_text", columnDefinition = "LONGTEXT")
    private String summaryText;

    /** 失败原因。2026-09-11 起不再写入（失败的任务直接删除记录，原因只落日志），保留列以兼容旧行。 */
    @Lob
    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(nullable = false)
    private boolean truncated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt = Instant.now();

    protected VideoSummary() {
        // JPA
    }

    public VideoSummary(Long userId, String videoUrl) {
        this.userId = userId;
        this.videoUrl = videoUrl;
        this.publicId = UuidCreator.getTimeOrderedEpoch().toString();
    }
}
