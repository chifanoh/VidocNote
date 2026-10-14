package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * yt-dlp 子进程配置。
 */
@ConfigurationProperties(prefix = "vidoc.ytdlp")
@Getter
@Setter
public class YtDlpProperties {

    /** 可执行文件路径或命令名（如 Docker 内 /usr/local/bin/yt-dlp）。 */
    private String path = "yt-dlp";

    /** 单次调用超时（秒），防止外部 URL 拖垮请求线程。 */
    private long timeoutSeconds = 120;

    /** 单次输出体积上限（字节），防止失控输出。 */
    private long maxOutputBytes = 10 * 1024 * 1024;

    /** 字幕等临时文件输出目录。 */
    private String workDir = System.getProperty("java.io.tmpdir");

    /**
     * 同时运行的 yt-dlp 进程上限。内存只被「抓取阶段」压（每个进程百 MB 量级），而任务池要对齐的是
     * 模型额度 —— 两件事分开控制：超出的调用在这里排队等，不失败。默认 16 是按「抓取阶段占比」估的。
     */
    private int maxConcurrency = 16;
}
