package com.vidoc.ytdlp;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * VTT/SRT 字幕 → 纯文本：剥离时间戳/序号/HTML 标签，合并为可读文本。
 * 超过 {@code maxChars} 时截断并置 {@code truncated=true}。
 */
@Component
public class SubtitleParser {

    public record Result(String text, boolean truncated) {
    }

    public Result parse(String content, long maxChars) {
        if (content == null || content.isBlank()) {
            return new Result("", false);
        }
        List<String> lines = new ArrayList<>(List.of(content.split("\\r?\\n", -1)));

        StringBuilder text = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (isSkippable(line)) {
                continue;
            }
            String cleaned = clean(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(cleaned);
        }

        String result = text.toString().trim();
        if (maxChars > 0 && result.length() > maxChars) {
            result = result.substring(0, (int) maxChars).trim();
            return new Result(result, true);
        }
        return new Result(result, false);
    }

    private boolean isSkippable(String line) {
        if (line.isEmpty()) {
            return true;
        }
        // VTT/SRT 时间轴、序号
        if (line.contains("-->")) {
            return true;
        }
        if (line.matches("\\d+")) {
            return true;
        }
        // VTT 头与元数据
        String upper = line.toUpperCase();
        return upper.startsWith("WEBVTT")
                || upper.startsWith("KIND:")
                || upper.startsWith("LANGUAGE:")
                || upper.startsWith("STYLE")
                || upper.startsWith("NOTE")
                || upper.startsWith("REGION:")
                || upper.startsWith("X-TIMESTAMP-MAP");
    }

    /** 去除行内标签（HTML / VTT 时间戳标签）与尾部 cue 设置。 */
    private String clean(String line) {
        String s = line.replaceAll("<[^>]*>", "");
        // VTT 时间戳标签如 <00:00:00.000>
        s = s.replaceAll("<\\d{2}:\\d{2}:\\d{2}[.,]?\\d*>", "");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }
}
