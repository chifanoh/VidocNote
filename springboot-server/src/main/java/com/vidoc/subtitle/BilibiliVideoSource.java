package com.vidoc.subtitle;

import com.vidoc.video.VideoSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/** B 站视频源：元数据与字幕全走匿名接口，免 cookie、免 yt-dlp。 */
@Component
@Order(10)
public class BilibiliVideoSource implements VideoSource {

    private final BilibiliSubtitleFetcher fetcher;

    public BilibiliVideoSource(BilibiliSubtitleFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public boolean supports(String url) {
        return isBilibiliUrl(url);
    }

    @Override
    public Optional<Metadata> metadata(String url) {
        return fetcher.fetchMetadata(url);
    }

    @Override
    public Optional<Transcript> transcript(String url, long maxChars) {
        return fetcher.fetch(url, maxChars);
    }

    /** B 站域名判定：bilibili.com（含子域）与短链 b23.tv。 */
    static boolean isBilibiliUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            String host = new URI(url).getHost();
            if (host != null) {
                String h = host.toLowerCase(Locale.ROOT);
                return h.equals("bilibili.com") || h.endsWith(".bilibili.com")
                        || h.equals("b23.tv") || h.endsWith(".b23.tv");
            }
        } catch (URISyntaxException ignored) {
            // 落到下方字符串匹配兜底
        }
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("bilibili.com") || u.contains("b23.tv");
    }
}
