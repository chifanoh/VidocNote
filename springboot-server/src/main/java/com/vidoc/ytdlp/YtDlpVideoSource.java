package com.vidoc.ytdlp;

import com.vidoc.video.VideoSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** 通用兜底视频源：没有平台专用源认领的链接一律交给 yt-dlp，故排在最后。 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class YtDlpVideoSource implements VideoSource {

    private final YtDlpService ytDlpService;
    private final SubtitleParser subtitleParser;

    public YtDlpVideoSource(YtDlpService ytDlpService, SubtitleParser subtitleParser) {
        this.ytDlpService = ytDlpService;
        this.subtitleParser = subtitleParser;
    }

    @Override
    public boolean supports(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    @Override
    public Optional<Metadata> metadata(String url) {
        return Optional.of(ytDlpService.fetchMetadata(url));
    }

    @Override
    public Optional<Transcript> transcript(String url, long maxChars) {
        // 抓取失败会抛 YtDlpException；只把「确实没有字幕文件」当空，不与失败混为一谈
        List<Path> files = ytDlpService.fetchSubtitleFiles(url);
        if (files.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder text = new StringBuilder();
        boolean truncated = false;
        try {
            for (Path file : files) {
                SubtitleParser.Result parsed = subtitleParser.parse(Files.readString(file), maxChars);
                if (parsed.text().isEmpty()) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(parsed.text());
                truncated = truncated || parsed.truncated();
                if (parsed.truncated()) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new YtDlpException("读取字幕文件失败：" + e.getMessage(), e);
        }

        String result = text.toString().trim();
        return result.isBlank() ? Optional.empty() : Optional.of(new Transcript(result, truncated));
    }
}
