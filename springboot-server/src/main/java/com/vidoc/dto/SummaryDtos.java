package com.vidoc.dto;

import com.vidoc.entity.SummaryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    public record CreateSummaryRequest(
            @NotBlank(message = "视频链接不能为空")
            @Size(max = 1024, message = "视频链接过长")
            @Pattern(regexp = "^https?://.+", message = "视频链接需以 http(s):// 开头")
            String videoUrl) {
    }

    /**
     * 历史列表项：刻意不带 {@code summaryText} / {@code errorMessage}（两个 LONGTEXT），
     * 列表页用不到大字段，带上会让每行都多读一次行外页。
     */
    public record SummaryListItem(
            String id,
            String videoUrl,
            String title,
            String author,
            String thumbnailUrl,
            Long duration,
            Instant createdAt) {
    }

    /**
     * 游标分页响应：{@code nextCursor} 为 null 表示没有下一页（Jackson 只输出非 null 字段）。
     * 不返回总条数——列表页只需要「还有没有下一页」。
     */
    public record CursorPage<T>(
            List<T> items,
            String nextCursor,
            boolean hasMore) {
    }

    public record SummaryResponse(
            String id,
            String videoUrl,
            String title,
            String author,
            String thumbnailUrl,
            Long duration,
            SummaryStatus status,
            String progressStep,
            String summaryText,
            String errorMessage,
            boolean truncated,
            Instant createdAt) {

        public static SummaryResponse from(com.vidoc.entity.VideoSummary s) {
            return new SummaryResponse(
                    s.getPublicId(),
                    s.getVideoUrl(),
                    s.getTitle(),
                    s.getAuthor(),
                    s.getThumbnailUrl(),
                    s.getDuration(),
                    s.getStatus(),
                    s.getProgressStep(),
                    s.getSummaryText(),
                    s.getErrorMessage(),
                    s.isTruncated(),
                    s.getCreatedAt());
        }

        /**
         * 记录已按「失败不落库」删除、但原因还留在登记处：用一条只带 id / 状态 / 原因的响应告诉调用方为什么失败。
         * 前端把它当普通 FAILED 处理，失败提示里就会显示 {@code errorMessage}。
         */
        public static SummaryResponse rejected(String publicId, String reason) {
            return new SummaryResponse(publicId, null, null, null, null, null,
                    SummaryStatus.FAILED, null, null, reason, false, null);
        }
    }
}
