package com.vidoc.subtitle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidoc.video.VideoSource.Metadata;
import com.vidoc.video.VideoSource.Transcript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * B 站专用匿名源（免 cookie / 免 yt-dlp），同时提供<strong>元数据</strong>与<strong>字幕</strong>。
 * <p>
 * 关键约束（2026-09 实测）：B 站对<strong>未签名</strong>的 {@code /x/web-interface/view} 做 IP 级安全风控 ——
 * 机房 IP（尤其境外）一律返回 HTTP 412「request was banned」，补 cookie、补 {@code dm_img_*}
 * 浏览器指纹参数都无效；而{@code 同一个域名}的 wbi 签名接口 {@code /x/web-interface/wbi/view}
 * 与弹幕/字幕接口 {@code /x/v2/dm/view} 都正常放行。因此元数据与字幕列表统一走 wbi 签名
 * （算法 = B 站 web 端那套 {@code md5(按 key 排序的 query + mixinKey)}），mixinKey 取自匿名可访问的
 * {@code /x/web-interface/nav}，缓存 30 分钟、失败即失效重取。链路：
 * <pre>
 *   GET /x/web-interface/nav                    → data.wbi_img：推导 mixinKey
 *   GET /x/web-interface/wbi/view?bvid=…&amp;p=N    → data：aid/cid(按 pages[] 取分 P)、title、pic、owner.name、duration
 *   GET /x/v2/dm/view?aid=…&amp;oid=…&amp;type=1       → data.subtitle.subtitles[]
 *   GET subtitle_url（带 auth_key 签名，须整串使用、升 https）→ body[].content
 * </pre>
 * 全程仅 UA + Referer，不携带任何 cookie。任一步失败或无结果都返回 {@link Optional#empty()}，
 * 由上层 {@link BilibiliVideoSource} 转成适配器结果。
 */
@Component
public class BilibiliSubtitleFetcher {

    private static final Logger log = LoggerFactory.getLogger(BilibiliSubtitleFetcher.class);

    /** 单次 HTTP 调用超时（连接 + 读取）。 */
    private static final int TIMEOUT_MS = 15_000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    private static final String BVID_PATTERN = "BV[0-9A-Za-z]{8,}";

    private static final String API_BASE = "https://api.bilibili.com";
    private static final String NAV_URL = API_BASE + "/x/web-interface/nav";
    private static final String WEB_REFERER = "https://www.bilibili.com";

    /** wbi 签名用的固定乱序表（B 站 web 端同一张表）。 */
    private static final int[] MIXIN_KEY_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52};

    /** mixinKey 每天轮换，缓存半小时足够，失败时立刻作废重取。 */
    private static final Duration MIXIN_KEY_TTL = Duration.ofMinutes(30);

    /** 人工上传的中文字幕语言码（优先于 AI 字幕）。 */
    private static final List<String> MANUAL_ZH = List.of(
            "zh", "zh-hans", "zh-hant", "zh-cn", "zh-tw", "zh-hk");

    private final ObjectMapper objectMapper;
    private final RestClient client;

    private volatile String mixinKey;
    private volatile Instant mixinKeyAt = Instant.EPOCH;

    public BilibiliSubtitleFetcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 抓取 B 站视频字幕为纯文本。任何异常 / 无字幕 → {@code Optional.empty()}。
     *
     * @param videoUrl B 站视频页 / 短链（b23.tv）
     * @param maxChars 转录超长截断上限
     */
    public Optional<Transcript> fetch(String videoUrl, long maxChars) {
        try {
            Optional<BilibiliVideoInfo> info = resolveVideo(videoUrl);
            if (info.isEmpty()) {
                return Optional.empty();
            }
            BilibiliVideoInfo video = info.get();
            String referer = pageReferer(videoUrl);

            JsonNode dm = getSignedJson("/x/v2/dm/view",
                    Map.of("aid", String.valueOf(video.aid()),
                            "oid", String.valueOf(video.cid()),
                            "type", "1"),
                    referer);
            JsonNode best = pickBestSubtitle(dm.path("data").path("subtitle").path("subtitles"));
            if (best == null) {
                log.debug("BilibiliSubtitleFetcher：该视频无可用字幕（dm/view 列表为空）");
                return Optional.empty();
            }

            String subUrl = normalizeHttpUrl(best.path("subtitle_url").asText(""));
            if (subUrl.isBlank()) {
                log.debug("BilibiliSubtitleFetcher：字幕条目缺少 subtitle_url");
                return Optional.empty();
            }

            JsonNode subRoot = getJson(subUrl, referer);
            String text = extractText(subRoot.path("body"));
            if (text.isBlank()) {
                log.debug("BilibiliSubtitleFetcher：字幕内容为空");
                return Optional.empty();
            }
            return Optional.of(Transcript.truncateTo(text, maxChars));
        } catch (Exception e) {
            log.debug("BilibiliSubtitleFetcher 失败，交由上层回退 yt-dlp：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 匿名抓取 B 站视频元数据（title / author / 封面 / 时长），无需 yt-dlp 与 cookie。
     * 链接无效或接口异常 → {@code Optional.empty()}。
     */
    public Optional<Metadata> fetchMetadata(String videoUrl) {
        return resolveVideo(videoUrl).flatMap(BilibiliSubtitleFetcher::toMetadata);
    }

    // ---- 一次 view 请求解析视频信息（元数据 + 选定 aid/cid） ----

    /**
     * GET {@code /x/web-interface/wbi/view}（wbi 签名）解析出 {@link BilibiliVideoInfo}。
     * 链接无法解析 / code≠0 / 网络异常 → {@code Optional.empty()}。
     */
    private Optional<BilibiliVideoInfo> resolveVideo(String videoUrl) {
        try {
            String bvid = parseBvid(videoUrl);
            long aidFromUrl = parseAid(videoUrl);
            if (bvid == null && aidFromUrl <= 0) {
                log.debug("BilibiliSubtitleFetcher：无法从 URL 解析 bvid/aid，跳过：{}", videoUrl);
                return Optional.empty();
            }
            int p = parseP(videoUrl);
            Map<String, String> params = new java.util.LinkedHashMap<>();
            if (bvid != null) {
                params.put("bvid", bvid);
            } else {
                params.put("aid", String.valueOf(aidFromUrl));
            }
            if (p >= 1) {
                params.put("p", String.valueOf(p));
            }
            JsonNode root = getSignedJson("/x/web-interface/wbi/view", params, pageReferer(videoUrl));
            return toVideoInfo(root.path("data"), p);
        } catch (Exception e) {
            // 提到 warn：失败原因（412 / 密钥 / 接口变更）是线上排查 B 站链路唯一线索，debug 会被日志级别吞掉
            log.warn("BilibiliSubtitleFetcher 解析 view 失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 构造视频页 Referer（用于风控），解析失败退回 B 站首页。 */
    private static String pageReferer(String videoUrl) {
        String bvid = parseBvid(videoUrl);
        if (bvid != null) {
            return "https://www.bilibili.com/video/" + bvid;
        }
        long aid = parseAid(videoUrl);
        if (aid > 0) {
            return "https://www.bilibili.com/video/" + aid;
        }
        return "https://www.bilibili.com";
    }

    // ---- HTTP ----

    /**
     * GET 并解析 JSON。仅当响应带 {@code code} 信封时校验其为 0；
     * aisubtitle 字幕内容 JSON 是裸文档（无 code 字段），直接放行。
     */
    private JsonNode getJson(String url, String referer) throws Exception {
        String body = getString(url, referer);
        JsonNode root = objectMapper.readTree(body);
        if (root.has("code") && root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("Bilibili 接口 code=" + root.path("code").asInt());
        }
        return root;
    }

    private String getString(String url, String referer) {
        return client.get()
                .uri(url)
                .header(HttpHeaders.REFERER, referer)
                .retrieve()
                .body(String.class);
    }

    /**
     * 不校验 {@code code} 的 GET。用于 {@code /x/web-interface/nav}：匿名访问它返回
     * {@code code:-101 账号未登录}，但 {@code data.wbi_img} 里的签名密钥依然有效 ——
     * 走校验版会被当成错误，导致 wbi 密钥永远取不到、B 站全链路失败。
     */
    private JsonNode getLooseJson(String url, String referer) throws Exception {
        return objectMapper.readTree(getString(url, referer));
    }

    // ---- wbi 签名 ----

    /** 带 wbi 签名的 GET。签名不符多半是 mixinKey 过期，作废重取后原样再试一次。 */
    private JsonNode getSignedJson(String path, Map<String, String> params, String referer) throws Exception {
        try {
            return getJson(signedUrl(path, params, currentMixinKey()), referer);
        } catch (Exception e) {
            invalidateMixinKey();
            return getJson(signedUrl(path, params, currentMixinKey()), referer);
        }
    }

    private String currentMixinKey() throws Exception {
        String cached = mixinKey;
        if (cached != null && Instant.now().isBefore(mixinKeyAt.plus(MIXIN_KEY_TTL))) {
            return cached;
        }
        synchronized (this) {
            if (mixinKey == null || !Instant.now().isBefore(mixinKeyAt.plus(MIXIN_KEY_TTL))) {
                JsonNode nav = getLooseJson(NAV_URL, WEB_REFERER);
                JsonNode wbiImg = nav.path("data").path("wbi_img");
                String key = mixinKeyOf(fileKey(wbiImg.path("img_url").asText("")),
                        fileKey(wbiImg.path("sub_url").asText("")));
                if (key.isBlank()) {
                    throw new IllegalStateException("nav 未返回可用的 wbi 密钥");
                }
                mixinKey = key;
                mixinKeyAt = Instant.now();
            }
            return mixinKey;
        }
    }

    private void invalidateMixinKey() {
        synchronized (this) {
            mixinKey = null;
            mixinKeyAt = Instant.EPOCH;
        }
    }

    private static String signedUrl(String path, Map<String, String> params, String mixinKey) {
        return API_BASE + path + "?" + wbiQuery(params, mixinKey, Instant.now().getEpochSecond());
    }

    // ---- 纯函数（可单测） ----

    /** 从 {@code .../7cd084941338484aae1ad9425b84077c.png} 取文件名主体（不含扩展名）。 */
    static String fileKey(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String name = url.substring(url.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 由 img_key + sub_key 按乱序表推出 32 位 mixinKey；长度不足返回空串。 */
    static String mixinKeyOf(String imgKey, String subKey) {
        String raw = (imgKey == null ? "" : imgKey) + (subKey == null ? "" : subKey);
        if (raw.length() < 64) {
            return "";
        }
        StringBuilder sb = new StringBuilder(32);
        for (int position : MIXIN_KEY_TAB) {
            sb.append(raw.charAt(position));
            if (sb.length() == 32) {
                break;
            }
        }
        return sb.toString();
    }

    /** 文档口径：先滤掉 {@code !'()*} 再百分号编码，空格编成 {@code %20} 而不是 {@code +}。 */
    static String wbiEncode(String value) {
        return URLEncoder.encode(value.replaceAll("[!'()*]", ""), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /** 拼出带 {@code w_rid} 的查询串：参数按 key 升序、附 wts、md5(query + mixinKey)。 */
    static String wbiQuery(Map<String, String> params, String mixinKey, long wts) {
        TreeMap<String, String> all = new TreeMap<>(params);
        all.put("wts", String.valueOf(wts));
        String query = all.entrySet().stream()
                .map(e -> wbiEncode(e.getKey()) + "=" + wbiEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        return query + "&w_rid=" + md5Hex(query + mixinKey);
    }

    static String md5Hex(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 MD5", e);
        }
    }

    /** 从 URL 中抽取 bvid（形如 BV + 10 位字母数字）；找不到返回 {@code null}。 */
    static String parseBvid(String url) {
        if (url == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(BVID_PATTERN).matcher(url);
        return m.find() ? m.group() : null;
    }

    /** 从 URL 中抽取纯数字 aid（新版数字 id，形如 {@code /video/116570663819359}）；找不到返回 0。 */
    static long parseAid(String url) {
        if (url == null) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:/video/|aid=)(\\d{8,})").matcher(url);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    /** 从 B 站 URL 提取分 P 序号（{@code ?p=N}、{@code &p=N}、{@code index_N.html}）；无则返回 0。 */
    static int parseP(String url) {
        if (url == null) {
            return 0;
        }
        java.util.regex.Matcher q = java.util.regex.Pattern.compile("[?&]p=(\\d+)").matcher(url);
        if (q.find()) {
            return Math.max(1, Integer.parseInt(q.group(1)));
        }
        java.util.regex.Matcher idx = java.util.regex.Pattern.compile("index_(\\d+)\\.html").matcher(url);
        if (idx.find()) {
            return Math.max(1, Integer.parseInt(idx.group(1)));
        }
        return 0;
    }

    /**
     * 把 view 接口的 {@code data} 节点映射为 {@link BilibiliVideoInfo}（含分 P cid 选择）。
     * 缺少 aid/cid 视为无效 → {@code Optional.empty()}。
     */
    static Optional<BilibiliVideoInfo> toVideoInfo(JsonNode data, int p) {
        if (data == null || !data.isObject()) {
            return Optional.empty();
        }
        long aid = data.path("aid").asLong();
        long cid = resolveCid(data, p);
        if (aid <= 0 || cid <= 0) {
            return Optional.empty();
        }
        long duration = data.path("duration").asLong(-1);
        return Optional.of(new BilibiliVideoInfo(
                aid,
                cid,
                data.path("title").asText(""),
                data.path("owner").path("name").asText(""),
                normalizeHttpUrl(data.path("pic").asText("")),
                duration >= 0 ? duration : null));
    }

    /**
     * 选择 cid：{@code p} 有效时取 {@code data.pages[p-1].cid}（分 P 视频），
     * 否则退回顶层 {@code data.cid}（单 P / 第一 P），再退回 pages 首条。
     */
    static long resolveCid(JsonNode data, int p) {
        JsonNode pages = data.path("pages");
        if (pages.isArray() && p >= 1 && p <= pages.size()) {
            long cid = pages.get(p - 1).path("cid").asLong();
            if (cid > 0) {
                return cid;
            }
        }
        long topCid = data.path("cid").asLong();
        if (topCid > 0) {
            return topCid;
        }
        if (pages.isArray() && !pages.isEmpty()) {
            return pages.get(0).path("cid").asLong();
        }
        return 0;
    }

    /** 纯映射：{@link BilibiliVideoInfo} → {@link Metadata}（title 为空视为无效）。 */
    static Optional<Metadata> toMetadata(BilibiliVideoInfo info) {
        if (info == null || info.title().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Metadata(info.title(), info.author(), info.thumbnailUrl(), info.duration()));
    }

    /**
     * 从 dm/view 返回的 subtitles 数组中选出最佳字幕条目：
     * 人工 zh ＞ ai-zh ＞ 其他 zh 变体 ＞ 人工 en ＞ ai-en ＞ 其他（按数组顺序）。
     */
    static JsonNode pickBestSubtitle(JsonNode subtitles) {
        if (subtitles == null || !subtitles.isArray() || subtitles.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        int bestRank = Integer.MAX_VALUE;
        for (JsonNode node : subtitles) {
            int rank = rankByLan(node.path("lan").asText(""));
            if (rank < bestRank) {
                bestRank = rank;
                best = node;
            }
        }
        return best;
    }

    private static int rankByLan(String lan) {
        if (lan == null || lan.isBlank()) {
            return 100;
        }
        String l = lan.trim().toLowerCase(Locale.ROOT);
        if (MANUAL_ZH.contains(l)) {
            return 0;
        }
        if (l.equals("ai-zh")) {
            return 1;
        }
        if (l.startsWith("zh")) {
            return 2;
        }
        if (l.equals("en") || l.equals("en-us") || l.equals("en-gb")) {
            return 3;
        }
        if (l.equals("ai-en")) {
            return 4;
        }
        return 5;
    }

    /** 规范化 http 地址：{@code //…} 补协议，{@code http://} 升 {@code https://}；整串保留（含签名参数）。 */
    static String normalizeHttpUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("//")) {
            return "https:" + s;
        }
        if (s.startsWith("http://")) {
            return "https://" + s.substring("http://".length());
        }
        return s;
    }

    /**
     * 把字幕 JSON 的 {@code body[]} 拼成纯文本：剥 HTML 标签、折叠空白、以空格串联非空句。
     */
    static String extractText(JsonNode body) {
        if (body == null || !body.isArray() || body.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : body) {
            String content = item.path("content").asText("");
            if (content.isBlank()) {
                continue;
            }
            String cleaned = content.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(cleaned);
        }
        return sb.toString().trim();
    }
}
