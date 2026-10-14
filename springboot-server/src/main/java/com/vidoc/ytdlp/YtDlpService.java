package com.vidoc.ytdlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidoc.config.YtDlpProperties;
import com.vidoc.video.VideoSource.Metadata;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 通过 {@link ProcessBuilder} 调用 yt-dlp（绝不走 shell，杜绝命令注入）：
 * - 元数据：{@code -J --skip-download}（JSON 输出）
 * - 字幕：{@code --skip-download --write-subs --write-auto-subs ...}
 * 命令带超时与输出体积上限。
 */
@Service
public class YtDlpService {

    private static final List<String> SUB_EXTENSIONS = List.of("vtt", "srt");

    private final YtDlpProperties properties;
    private final ObjectMapper objectMapper;
    private final ProcessGate gate;

    public YtDlpService(YtDlpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.gate = new ProcessGate(properties.getMaxConcurrency());
    }

    /**
     * 抓取视频元数据。
     *
     * @throws YtDlpException 命令失败 / 超时 / 输出超限 / JSON 不可解析
     */
    public Metadata fetchMetadata(String url) {
        List<String> command = new ArrayList<>();
        command.add(properties.getPath());
        command.add("-J");
        command.add("--skip-download");
        command.add("--no-playlist");
        command.add(url);

        String output = gate.call(() -> runProcess(command));
        try {
            JsonNode root = objectMapper.readTree(output);
            String title = root.path("title").asText();
            if (title.isBlank()) {
                throw new YtDlpException("未能从该链接解析出视频标题");
            }
            String author = root.path("uploader").asText(null);
            String thumbnail = firstNonBlank(
                    root.path("thumbnail").asText(null),
                    root.path("thumbnails").isArray() && !root.path("thumbnails").isEmpty()
                            ? root.path("thumbnails").get(root.path("thumbnails").size() - 1).path("url").asText(null)
                            : null);
            long duration = root.path("duration").asLong(-1);
            return new Metadata(title, author, thumbnail, duration >= 0 ? duration : null);
        } catch (IOException e) {
            throw new YtDlpException("yt-dlp 元数据输出无法解析：" + e.getMessage(), e);
        }
    }

    /**
     * 下载字幕文件到临时目录，返回落盘文件路径列表（可为空，空即该视频确实没有字幕）。
     *
     * @throws YtDlpException 命令失败 / 超时 / 输出超限；真实原因向上抛，不在本层吞成「无字幕」
     */
    public List<Path> fetchSubtitleFiles(String url) {
        Path workDir = createWorkDir();
        try {
            List<String> command = new ArrayList<>();
            command.add(properties.getPath());
            command.add("--skip-download");
            command.add("--write-subs");
            command.add("--write-auto-subs");
            command.add("--sub-langs");
            command.add("all");
            command.add("--sub-format");
            command.add("vtt/srt/best");
            command.add("--no-playlist");
            command.add("-o");
            command.add(workDir.resolve("%(id)s.%(ext)s").toString());
            command.add(url);

            gate.run(() -> runProcess(command));
            return findSubtitleFiles(workDir);
        } finally {
            // 字幕解析在上游完成后才调用，这里不删除；由调用方负责清理
        }
    }

    /** 解析出目录下的字幕文件（vtt/srt），按文件名排序保证稳定。 */
    private List<Path> findSubtitleFiles(Path dir) throws YtDlpException {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return SUB_EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(found::add);
        } catch (IOException e) {
            throw new YtDlpException("读取字幕目录失败：" + e.getMessage(), e);
        }
        return found;
    }

    private Path createWorkDir() {
        try {
            Path base = Path.of(properties.getWorkDir());
            Files.createDirectories(base);
            return Files.createTempDirectory(base, "vidoc-subs-");
        } catch (IOException e) {
            throw new YtDlpException("创建临时目录失败：" + e.getMessage(), e);
        }
    }

    /**
     * 执行子进程，返回合并输出（stdout+stderr），带超时与体积上限。
     * 读取在独立线程进行，避免管道缓冲区占满导致进程卡死。
     */
    private String runProcess(List<String> command) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new YtDlpException("无法启动 yt-dlp（路径：" + properties.getPath()
                    + "）。请确认已安装并配置 VIDOC_YTDLP_PATH。" + e.getMessage(), e);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AtomicReference<Exception> readError = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = process.getInputStream().read(chunk)) != -1) {
                    if (buffer.size() + read > properties.getMaxOutputBytes()) {
                        process.destroyForcibly();
                        throw new IOException("输出超过体积上限（" + properties.getMaxOutputBytes() + " 字节）");
                    }
                    buffer.write(chunk, 0, read);
                }
            } catch (IOException e) {
                readError.set(e);
            }
        }, "yt-dlp-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new YtDlpException("yt-dlp 调用被中断", e);
        }

        try {
            reader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!finished) {
            process.destroyForcibly();
            throw new YtDlpException("yt-dlp 执行超时（>" + properties.getTimeoutSeconds() + " 秒）");
        }
        if (readError.get() != null) {
            throw new YtDlpException("读取 yt-dlp 输出失败：" + readError.get().getMessage(), readError.get());
        }
        if (process.exitValue() != 0) {
            // 把 yt-dlp 的真实输出尾部带进报错，避免“退出码 1”无从查起
            String full = buffer.toString(StandardCharsets.UTF_8).trim();
            String tail = full.length() > 300 ? full.substring(full.length() - 300) : full;
            throw new YtDlpException("yt-dlp 执行失败（退出码 " + process.exitValue() + "）："
                    + (tail.isBlank() ? "(无输出)" : tail));
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
