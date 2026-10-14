package com.vidoc.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointsCostTest {

    private static int costOfMinutes(long minutes) {
        return PointsService.costOf(minutes * 60);
    }

    @Test
    void chargesMinimumForShortVideos() {
        assertThat(costOfMinutes(0)).isEqualTo(10);
        assertThat(costOfMinutes(1)).isEqualTo(10);
        assertThat(costOfMinutes(10)).isEqualTo(10);
    }

    @Test
    void roundsPartialMinuteUp() {
        assertThat(PointsService.costOf(61L)).isEqualTo(10);
        assertThat(PointsService.costOf(11L * 60)).isEqualTo(11);
        assertThat(PointsService.costOf(11L * 60 + 1)).isEqualTo(12);
    }

    @Test
    void isLinearUpToAnHour() {
        assertThat(costOfMinutes(30)).isEqualTo(30);
        assertThat(costOfMinutes(45)).isEqualTo(45);
        assertThat(costOfMinutes(60)).isEqualTo(60);
    }

    @Test
    void stepsUpPastAnHour() {
        assertThat(costOfMinutes(90)).isEqualTo(105);
        assertThat(costOfMinutes(120)).isEqualTo(150);
        assertThat(costOfMinutes(180)).isEqualTo(240);
    }

    @Test
    void stepsUpPastThreeHours() {
        assertThat(costOfMinutes(240)).isEqualTo(360);
    }

    @Test
    void hasNoJumpBetweenTiers() {
        assertThat(costOfMinutes(60)).isEqualTo(60);
        assertThat(costOfMinutes(61)).isEqualTo(62);
        assertThat(costOfMinutes(180)).isEqualTo(240);
        assertThat(costOfMinutes(181)).isEqualTo(242);
    }

    @Test
    void unknownDurationFallsBackToMinimum() {
        assertThat(PointsService.costOf(null)).isEqualTo(10);
        assertThat(PointsService.costOf(0L)).isEqualTo(10);
        assertThat(PointsService.costOf(-5L)).isEqualTo(10);
    }
}
