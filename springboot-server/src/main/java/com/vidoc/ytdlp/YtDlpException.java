package com.vidoc.ytdlp;

/**
 * yt-dlp 调用失败（未安装 / 超时 / 输出超限 / 平台错误等）。
 */
public class YtDlpException extends RuntimeException {

    public YtDlpException(String message) {
        super(message);
    }

    public YtDlpException(String message, Throwable cause) {
        super(message, cause);
    }
}
