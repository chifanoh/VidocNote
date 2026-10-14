package com.vidoc.video;

import java.util.Optional;

/**
 * 视频源适配器：一个平台一个实现，由 {@link VideoSourceRegistry} 选用。
 * 新增平台只需新增实现类并标注 {@code @Component}，摘要主流程不动。
 */
public interface VideoSource {

    /** 该链接是否由本源处理；实现须对 null / 非法 URL 返回 false，不得抛异常。 */
    boolean supports(String url);

    /** 视频元数据；链接无效或平台查无此视频时为空。 */
    Optional<Metadata> metadata(String url);

    /** 字幕转录；平台确实没有字幕时为空，抓取过程出错则抛异常（真实原因不在本层吞掉）。 */
    Optional<Transcript> transcript(String url, long maxChars);

    /** 视频元数据，来源由实现决定（平台接口或 yt-dlp）。 */
    record Metadata(String title, String author, String thumbnailUrl, Long duration) {
    }

    /** 字幕文本 + 是否因超长被截断；语义对齐 {@link com.vidoc.ytdlp.SubtitleParser.Result}。 */
    record Transcript(String text, boolean truncated) {

        /** 按 {@code maxChars} 截断，超出置 {@code truncated=true}。 */
        public static Transcript truncateTo(String raw, long maxChars) {
            if (raw == null) {
                return new Transcript("", false);
            }
            String t = raw.trim();
            if (maxChars > 0 && t.length() > maxChars) {
                return new Transcript(t.substring(0, (int) maxChars).trim(), true);
            }
            return new Transcript(t, false);
        }
    }
}
