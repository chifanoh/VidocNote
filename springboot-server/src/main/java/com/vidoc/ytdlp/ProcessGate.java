package com.vidoc.ytdlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * 子进程并发闸门：限制同时运行的 yt-dlp 进程数。
 * <p>**等待而不是拒绝** —— 它是内存保护（每个进程百 MB 量级），不是限流：任务池满了才 503，
 * 到这里的调用只会慢一点。公平模式，避免后到的调用被一直插队。
 */
public class ProcessGate {

    private static final Logger log = LoggerFactory.getLogger(ProcessGate.class);

    /** 等超过这个时长就记一条：说明抓取阶段成了瓶颈，该调大闸门或查上游了 */
    private static final long WAIT_WARN_MS = 5_000;

    private final Semaphore slots;
    private final int limit;

    public ProcessGate(int limit) {
        this.limit = Math.max(1, limit);
        this.slots = new Semaphore(this.limit, true);
        log.info("yt-dlp 并发上限：{}（超出的调用排队等待，不失败）", this.limit);
    }

    public <T> T call(Supplier<T> action) {
        long since = System.currentTimeMillis();
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YtDlpException("等待 yt-dlp 并发许可时被中断", e);
        }
        long waited = System.currentTimeMillis() - since;
        if (waited > WAIT_WARN_MS) {
            log.warn("yt-dlp 等待并发许可 {} ms（上限 {}）", waited, limit);
        }
        try {
            return action.get();
        } finally {
            slots.release();
        }
    }

    public void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
