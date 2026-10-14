package com.vidoc.service;

import com.vidoc.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 历史列表的游标编解码（对外只是不透明串，不含内部自增主键）。
 * <p>内容是 base64url("{微秒时间戳}:{publicId}")，对应排序键 (created_at, public_id)。
 * 时间精度取微秒而非毫秒：created_at 列是 datetime(6)，按毫秒编码会把同一毫秒内的
 * 其余记录判成「不在游标之后」而漏掉。
 */
final class SummaryCursor {

    private SummaryCursor() {
    }

    record Value(Instant createdAt, String publicId) {
    }

    static String encode(Instant createdAt, String publicId) {
        long micros = Math.addExact(Math.multiplyExact(createdAt.getEpochSecond(), 1_000_000L),
                createdAt.getNano() / 1000);
        String raw = micros + ":" + publicId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析失败统一 400：游标是服务端发的不透明串，客户端只该原样回传。 */
    static Value decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.indexOf(':');
            if (split <= 0 || split == raw.length() - 1) {
                throw new IllegalArgumentException("游标缺少分隔符");
            }
            long micros = Long.parseLong(raw.substring(0, split));
            Instant createdAt = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                    Math.floorMod(micros, 1_000_000L) * 1000L);
            return new Value(createdAt, raw.substring(split + 1));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "分页游标无效");
        }
    }
}
