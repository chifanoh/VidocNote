package com.vidoc.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 最近失败原因的短生命周期登记处，**只在内存里，不落库**。
 * 失败的任务按「失败不落库」当场删除，用户轮询只会拿到 404、无从知道为什么没生成出来；
 * 这里保留几分钟原因，让查询接口把 404 换成一个带 errorMessage 的 FAILED 响应。
 */
@Component
public class RejectionLog {

    /** 前端轮询间隔 2.5 秒；留 10 分钟是为了让用户中途刷新页面也还能看到原因 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /** 只为兜住「被刷」时的内存：每条几十字节，1000 条封顶 */
    private static final int MAX_ENTRIES = 1000;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;

    public RejectionLog() {
        this(DEFAULT_TTL);
    }

    RejectionLog(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(String publicId, String reason) {
        Instant now = Instant.now();
        entries.values().removeIf(entry -> expired(entry, now));
        if (entries.size() >= MAX_ENTRIES) {
            dropOldest();
        }
        entries.put(publicId, new Entry(reason, now));
    }

    /** 该记录最近失败过就返回原因；过期或从未失败过返回空。 */
    public Optional<String> reasonOf(String publicId) {
        Entry entry = entries.get(publicId);
        if (entry == null) {
            return Optional.empty();
        }
        if (expired(entry, Instant.now())) {
            entries.remove(publicId);
            return Optional.empty();
        }
        return Optional.of(entry.reason());
    }

    private boolean expired(Entry entry, Instant now) {
        return entry.at().plus(ttl).isBefore(now);
    }

    private void dropOldest() {
        entries.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> a.at().compareTo(b.at())))
                .map(Map.Entry::getKey)
                .ifPresent(entries::remove);
    }

    private record Entry(String reason, Instant at) {
    }
}
