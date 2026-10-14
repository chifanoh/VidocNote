package com.vidoc.ytdlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubtitleParserTest {

    private final SubtitleParser parser = new SubtitleParser();

    @Test
    void stripsVttTimestampsAndTags() {
        String vtt = """
                WEBVTT

                00:00:00.000 --> 00:00:02.500
                <v Roger Bingham>大家好

                00:00:02.500 --> 00:00:05.000
                这是<00:00:03.000>一个测试字幕

                00:00:05.000 --> 00:00:08.000
                （掌声）
                """;
        SubtitleParser.Result result = parser.parse(vtt, 10_000);
        assertThat(result.text()).isEqualTo("大家好 这是一个测试字幕 （掌声）");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void stripsSrtIndexAndTiming() {
        String srt = """
                1
                00:00:00,000 --> 00:00:02,500
                第一行字幕

                2
                00:00:02,500 --> 00:00:05,000
                第二行字幕
                """;
        SubtitleParser.Result result = parser.parse(srt, 10_000);
        assertThat(result.text()).isEqualTo("第一行字幕 第二行字幕");
    }

    @Test
    void emptyInputReturnsEmpty() {
        SubtitleParser.Result result = parser.parse("   \n\n  ", 10_000);
        assertThat(result.text()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void nullInputReturnsEmpty() {
        SubtitleParser.Result result = parser.parse(null, 10_000);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void truncatesLongTextAndMarksTruncated() {
        String content = "这是一段比较长的字幕内容，用于验证截断逻辑是否正确。";
        SubtitleParser.Result result = parser.parse(content, 8);
        assertThat(result.text()).hasSizeLessThanOrEqualTo(8);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void ignoresHtmlTagsInCue() {
        String vtt = """
                WEBVTT

                00:00:00.000 --> 00:00:02.000
                <c.color000000>加粗内容</c> <i>斜体</i>
                """;
        SubtitleParser.Result result = parser.parse(vtt, 10_000);
        assertThat(result.text()).isEqualTo("加粗内容 斜体");
    }
}
