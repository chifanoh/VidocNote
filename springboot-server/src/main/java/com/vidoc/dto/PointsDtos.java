package com.vidoc.dto;

import com.vidoc.entity.PointsReason;

import java.time.Instant;

public final class PointsDtos {

    private PointsDtos() {
    }

    public record PointsSummaryResponse(int balance) {
    }

    public record PointsLedgerItem(
            Instant createdAt,
            PointsReason reason,
            int delta,
            String summaryId) {
    }
}
