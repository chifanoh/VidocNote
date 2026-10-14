package com.vidoc.video;

import com.vidoc.subtitle.BilibiliVideoSource;
import com.vidoc.ytdlp.YtDlpException;
import com.vidoc.ytdlp.YtDlpVideoSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 选源规则的纯逻辑测试（不打网络）。 */
class VideoSourceRegistryTest {

    /** 只关心 supports 的桩适配器，顺序由 @Order 决定 */
    @Order(10)
    private static class Stub implements VideoSource {

        private final boolean supports;

        Stub(boolean supports) {
            this.supports = supports;
        }

        @Override
        public boolean supports(String url) {
            return supports;
        }

        @Override
        public Optional<Metadata> metadata(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Transcript> transcript(String url, long maxChars) {
            throw new UnsupportedOperationException();
        }
    }

    @Order(1000)
    private static class LateStub extends Stub {
        LateStub(boolean supports) {
            super(supports);
        }
    }

    @Test
    void 同时认领时取Order小者_与注入顺序无关() {
        // 故意把兜底桩放在前面注入
        assertSame(Stub.class,
                new VideoSourceRegistry(List.of(new LateStub(true), new Stub(true))).resolve("https://x/y").getClass());
        assertSame(Stub.class,
                new VideoSourceRegistry(List.of(new Stub(true), new LateStub(true))).resolve("https://x/y").getClass());
    }

    @Test
    void 无人认领时抛可读原因() {
        VideoSourceRegistry registry = new VideoSourceRegistry(List.of(new Stub(false)));
        YtDlpException e = assertThrows(YtDlpException.class, () -> registry.resolve("https://x/y"));
        assertTrue(e.getMessage().contains("无法识别"));
    }

    @Test
    void sources按Order排序() {
        VideoSourceRegistry registry = new VideoSourceRegistry(List.of(new LateStub(true), new Stub(true)));
        assertEquals(List.of(Stub.class, LateStub.class),
                registry.sources().stream().map(Object::getClass).toList());
    }

    // ---- 与真实适配器一起验证平台归口 ----

    @Test
    void B站链接归平台专用源_其余归兜底源() {
        // supports 不碰内部依赖，故可传 null 构造
        VideoSource bilibili = new BilibiliVideoSource(null);
        VideoSource ytdlp = new YtDlpVideoSource(null, null);
        // 乱序注入：兜底源排在前面也不该抢走 B 站链接
        VideoSourceRegistry registry = new VideoSourceRegistry(List.of(ytdlp, bilibili));

        assertSame(bilibili, registry.resolve("https://www.bilibili.com/video/BV1Rh5S6KEtv"));
        assertSame(bilibili, registry.resolve("https://b23.tv/BV1Rh5S6KEtv"));
        assertSame(ytdlp, registry.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertSame(ytdlp, registry.resolve("https://video.weibo.com/show?fid=1034:1234"));
    }

    @Test
    void 兜底源只认http链接_其余视为无法识别() {
        VideoSource ytdlp = new YtDlpVideoSource(null, null);

        assertTrue(ytdlp.supports("https://example.com/v/1"));
        assertTrue(ytdlp.supports("http://example.com/v/1"));
        assertFalse(ytdlp.supports("ftp://example.com/v/1"));
        assertFalse(ytdlp.supports("随便一段文字"));
        assertFalse(ytdlp.supports(null));

        VideoSourceRegistry registry = new VideoSourceRegistry(List.of(ytdlp));
        assertThrows(YtDlpException.class, () -> registry.resolve("这是一段文字，不是链接"));
    }
}
