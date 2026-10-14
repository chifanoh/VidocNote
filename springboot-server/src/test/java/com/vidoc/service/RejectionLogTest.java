package com.vidoc.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RejectionLogTest {

    @Test
    void remembersTheReasonOfARecentFailure() {
        RejectionLog rejections = new RejectionLog();

        rejections.put("id-1", "该视频没有可用字幕，暂不支持生成摘要");

        assertThat(rejections.reasonOf("id-1")).contains("该视频没有可用字幕，暂不支持生成摘要");
    }

    @Test
    void forgetsUnknownAndExpiredIds() throws Exception {
        RejectionLog rejections = new RejectionLog(Duration.ofMillis(30));
        rejections.put("id-1", "视频时长超过 3 小时上限，暂不支持");

        assertThat(rejections.reasonOf("id-2")).isEmpty();

        Thread.sleep(60);
        assertThat(rejections.reasonOf("id-1")).isEmpty();
    }

    /** 条目数封顶：塞满之后最老的被挤出去，内存不随失败次数无限增长 */
    @Test
    void keepsAtMostTheConfiguredNumberOfEntries() {
        RejectionLog rejections = new RejectionLog();
        for (int i = 0; i < 1200; i++) {
            rejections.put("id-" + i, "原因 " + i);
        }

        assertThat(rejections.reasonOf("id-0")).isEmpty();
        assertThat(rejections.reasonOf("id-1199")).contains("原因 1199");
    }
}
