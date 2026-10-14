package com.vidoc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时为存量用户补建积分行（幂等）。
 */
@Component
public class PointsStartupBackfill {

    private static final Logger log = LoggerFactory.getLogger(PointsStartupBackfill.class);

    private final PointsService pointsService;

    public PointsStartupBackfill(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        int created = pointsService.backfillMissingRows();
        if (created > 0) {
            log.info("已为 {} 个存量用户补建积分行", created);
        }
    }
}
