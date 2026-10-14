package com.vidoc.service;

import com.vidoc.entity.SummaryStatus;
import com.vidoc.repository.VideoSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 启动时清理上次进程遗留的未完成任务。
 */
@Component
public class SummaryStartupCleaner {

    private static final Logger log = LoggerFactory.getLogger(SummaryStartupCleaner.class);

    private final VideoSummaryRepository repository;
    /** 本进程启动时刻：只清比它更早的行，避免误删启动这几毫秒内新提交的任务 */
    private final Instant bootAt = Instant.now();

    public SummaryStartupCleaner(VideoSummaryRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanOrphans() {
        int removed = repository.deleteUnfinishedBefore(
                List.of(SummaryStatus.PENDING, SummaryStatus.PROCESSING), bootAt);
        if (removed > 0) {
            log.info("已清理上次运行遗留的未完成摘要 {} 条", removed);
        }
    }
}
