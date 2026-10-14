package com.vidoc.service;

import com.vidoc.dto.SummaryDtos;
import com.vidoc.entity.SummaryStatus;
import com.vidoc.entity.VideoSummary;
import com.vidoc.exception.ApiException;
import com.vidoc.repository.VideoSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.Executor;

@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final VideoSummaryRepository repository;
    private final SummaryProcessor processor;
    private final PointsService pointsService;
    private final RejectionLog rejections;
    private final Executor summaryExecutor;

    public SummaryService(VideoSummaryRepository repository, SummaryProcessor processor, PointsService pointsService,
                          RejectionLog rejections,
                          @Qualifier("summaryExecutor") Executor summaryExecutor) {
        this.repository = repository;
        this.processor = processor;
        this.pointsService = pointsService;
        this.rejections = rejections;
        this.summaryExecutor = summaryExecutor;
    }

    /**
     * 创建摘要任务：立即落库（PENDING）并投进线程池，返回任务实体。
     * <p>注意：这里刻意不包 {@code @Transactional}——否则外层事务尚未提交、异步
     * {@code SummaryProcessor.process} 已抢先执行，其 {@code findById} 读不到新行会
     * 静默返回，导致摘要永久停在 PENDING。{@code repository.save} 自管事务即可。
     * <p>这条 PENDING 记录只是异步处理的句柄：成功转 COMPLETED，失败由
     * {@code SummaryProcessor} 删除，故库里最终只剩完成的任务。
     * <p>提交前先校验余额够单条起步价：不够就连记录都不创建。
     * 真实扣分要等拿到视频时长，见 {@code SummaryProcessor}。
     */
    public VideoSummary create(Long userId, SummaryDtos.CreateSummaryRequest request) {
        if (pointsService.balance(userId) < PointsService.MIN_COST) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "积分不足，无法生成摘要");
        }
        VideoSummary created = repository.save(new VideoSummary(userId, request.videoUrl().trim()));
        try {
            summaryExecutor.execute(() -> processor.process(created.getId()));
        } catch (TaskRejectedException e) {
            // 线程池与队列都满：刚存的 PENDING 行没人会处理，必须删掉，否则永远滞留在库里
            log.warn("排队已满，丢弃刚创建的记录 {}", created.getPublicId());
            repository.delete(created);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "任务繁忙，请稍后重试");
        }
        return created;
    }

    /**
     * 按用户倒序（新在前）查自己的历史记录，游标分页：{@code cursor} 为空取第一页，
     * 否则取上一页 nextCursor 之后的部分（不透明串，见 {@link SummaryCursor}）。
     * <p>只返回 COMPLETED：失败的任务已丢弃，进行中的任务（含重启后滞留的）不展示给用户。
     * <p>多取一条用于判断还有没有下一页，因此不发 count 查询。
     */
    @Transactional(readOnly = true)
    public SummaryDtos.CursorPage<SummaryDtos.SummaryListItem> list(Long userId, String cursor, int size) {
        Pageable fetch = PageRequest.of(0, size + 1);
        List<SummaryDtos.SummaryListItem> rows = (cursor == null || cursor.isBlank())
                ? repository.findListPage(userId, SummaryStatus.COMPLETED, fetch)
                : findByCursor(userId, cursor, fetch);

        boolean hasMore = rows.size() > size;
        List<SummaryDtos.SummaryListItem> items = hasMore ? List.copyOf(rows.subList(0, size)) : rows;
        String nextCursor = hasMore ? encodeCursor(items.get(items.size() - 1)) : null;
        return new SummaryDtos.CursorPage<>(items, nextCursor, hasMore);
    }

    private List<SummaryDtos.SummaryListItem> findByCursor(Long userId, String cursor, Pageable fetch) {
        SummaryCursor.Value value = SummaryCursor.decode(cursor);
        return repository.findListPageAfter(userId, SummaryStatus.COMPLETED, value.createdAt(), value.publicId(), fetch);
    }

    private static String encodeCursor(SummaryDtos.SummaryListItem last) {
        return SummaryCursor.encode(last.createdAt(), last.id());
    }

    /**
     * 按对外标识查询单条记录并校验归属：别人的记录与不存在的记录都查不到。
     * <p>失败的任务已按「失败不落库」删除，正常情况下只剩 404；若 {@link RejectionLog} 里还留着这次失败的原因，
     * 就返回一条只有 id / FAILED / errorMessage 的响应，让用户知道为什么没生成出来。
     */
    @Transactional(readOnly = true)
    public SummaryDtos.SummaryResponse getOwned(Long userId, String publicId) {
        VideoSummary found = findOwned(userId, publicId);
        if (found != null) {
            return SummaryDtos.SummaryResponse.from(found);
        }
        return rejections.reasonOf(publicId)
                .map(reason -> SummaryDtos.SummaryResponse.rejected(publicId, reason))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "摘要记录不存在"));
    }

    private VideoSummary findOwned(Long userId, String publicId) {
        return repository.findByPublicIdAndUserId(publicId, userId).orElse(null);
    }

    /**
     * 删除自己的记录，同样校验归属。
     */
    @Transactional
    public void deleteOwned(Long userId, String publicId) {
        VideoSummary summary = findOwned(userId, publicId);
        if (summary == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "摘要记录不存在");
        }
        repository.delete(summary);
    }
}
