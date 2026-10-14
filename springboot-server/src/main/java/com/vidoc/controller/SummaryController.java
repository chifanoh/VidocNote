package com.vidoc.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.vidoc.dto.SummaryDtos;
import com.vidoc.entity.VideoSummary;
import com.vidoc.service.SummaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 摘要 API。所有接口按当前登录用户隔离。
 */
@RestController
@RequestMapping("/api/summaries")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SummaryDtos.SummaryResponse create(@Valid @RequestBody SummaryDtos.CreateSummaryRequest request) {
        VideoSummary summary = summaryService.create(currentUserId(), request);
        return SummaryDtos.SummaryResponse.from(summary);
    }

    /**
     * 历史列表。游标分页：首次不传 cursor，之后原样回传上一页的 nextCursor。
     * 列表项刻意不含 summaryText（见 SummaryListItem），卡片用不到。
     */
    @GetMapping
    public SummaryDtos.CursorPage<SummaryDtos.SummaryListItem> list(@RequestParam(required = false) String cursor,
                                                                    @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return summaryService.list(currentUserId(), cursor, safeSize);
    }

    /** 路径上的 id 是对外标识（UUIDv7），不是数据库主键。 */
    @GetMapping("/{id}")
    public SummaryDtos.SummaryResponse get(@PathVariable String id) {
        return summaryService.getOwned(currentUserId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        summaryService.deleteOwned(currentUserId(), id);
    }

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}
