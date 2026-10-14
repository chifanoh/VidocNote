package com.vidoc.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.vidoc.dto.PointsDtos;
import com.vidoc.service.PointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 积分查询 */
@RestController
@RequestMapping("/api/points")
public class PointsController {

    private static final int LEDGER_DEFAULT_SIZE = 10;
    private static final int LEDGER_MAX_SIZE = 50;

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @GetMapping
    public PointsDtos.PointsSummaryResponse summary() {
        return new PointsDtos.PointsSummaryResponse(pointsService.balance(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/ledger")
    public List<PointsDtos.PointsLedgerItem> ledger(
            @RequestParam(defaultValue = "" + LEDGER_DEFAULT_SIZE) int size) {
        int safeSize = Math.min(Math.max(size, 1), LEDGER_MAX_SIZE);
        return pointsService.recent(StpUtil.getLoginIdAsLong(), safeSize);
    }
}
