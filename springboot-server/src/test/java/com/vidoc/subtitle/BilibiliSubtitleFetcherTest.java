package com.vidoc.subtitle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidoc.video.VideoSource.Transcript;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BilibiliSubtitleFetcher / BilibiliVideoSource 的纯函数测试（不打网络）。
 */
class BilibiliSubtitleFetcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---- parseBvid / parseAid ----

    @Test
    void parseBvid_从各类URL抽取BV号() {
        assertEquals("BV1Rh5S6KEtv", BilibiliSubtitleFetcher.parseBvid(
                "https://www.bilibili.com/video/BV1Rh5S6KEtv/?spm_id_from=333.1391.0.0"));
        assertEquals("BV1Rh5S6KEtv", BilibiliSubtitleFetcher.parseBvid("https://b23.tv/BV1Rh5S6KEtv"));
        assertNull(BilibiliSubtitleFetcher.parseBvid("https://example.com/watch/abc"));
        assertNull(BilibiliSubtitleFetcher.parseBvid(null));
    }

    @Test
    void parseAid_抽取纯数字id() {
        assertEquals(116570663819359L, BilibiliSubtitleFetcher.parseAid(
                "https://www.bilibili.com/video/116570663819359"));
        assertEquals(116570663819359L, BilibiliSubtitleFetcher.parseAid("?aid=116570663819359"));
        assertEquals(0L, BilibiliSubtitleFetcher.parseAid("https://www.bilibili.com/video/BV1Rh5S6KEtv"));
    }

    // ---- pickBestSubtitle ----

    @Test
    void pick_人工中文字幕最优先() throws Exception {
        JsonNode subs = MAPPER.readTree("""
                [
                  {"lan": "ai-zh", "subtitle_url": "https://x/ai-zh"},
                  {"lan": "zh-Hans", "subtitle_url": "https://x/zh-hans"},
                  {"lan": "ai-en", "subtitle_url": "https://x/ai-en"}
                ]
                """);
        assertEquals("zh-Hans", BilibiliSubtitleFetcher.pickBestSubtitle(subs).path("lan").asText());
    }

    @Test
    void pick_无人工时AI中文优先于英文() throws Exception {
        JsonNode subs = MAPPER.readTree("""
                [
                  {"lan": "en", "subtitle_url": "https://x/en"},
                  {"lan": "ai-zh", "subtitle_url": "https://x/ai-zh"}
                ]
                """);
        assertEquals("ai-zh", BilibiliSubtitleFetcher.pickBestSubtitle(subs).path("lan").asText());
    }

    @Test
    void pick_空列表返回null() {
        assertNull(BilibiliSubtitleFetcher.pickBestSubtitle(MAPPER.createArrayNode()));
        assertNull(BilibiliSubtitleFetcher.pickBestSubtitle(null));
    }

    // ---- normalizeHttpUrl ----

    @Test
    void normalize_补协议并保留auth_key() {
        assertEquals("https://aisubtitle.hdslb.com/bfs/x?auth_key=178", BilibiliSubtitleFetcher.normalizeHttpUrl(
                "http://aisubtitle.hdslb.com/bfs/x?auth_key=178"));
        assertEquals("https://aisubtitle.hdslb.com/bfs/x?auth_key=178", BilibiliSubtitleFetcher.normalizeHttpUrl(
                "//aisubtitle.hdslb.com/bfs/x?auth_key=178"));
        assertEquals("https://aisubtitle.hdslb.com/bfs/x?auth_key=178", BilibiliSubtitleFetcher.normalizeHttpUrl(
                "https://aisubtitle.hdslb.com/bfs/x?auth_key=178"));
        assertEquals("", BilibiliSubtitleFetcher.normalizeHttpUrl(""));
    }

    // ---- extractText + 截断 ----

    @Test
    void extractText_拼接正文并去标签() throws Exception {
        JsonNode body = MAPPER.readTree("""
                [{"content": "第一句<v>标签</v>"}, {"content": "第二句"}, {"content": ""}]
                """);
        assertEquals("第一句标签 第二句", BilibiliSubtitleFetcher.extractText(body));
        assertEquals("", BilibiliSubtitleFetcher.extractText(MAPPER.createArrayNode()));
    }

    @Test
    void truncateTo_超长置截断标记() {
        Transcript t = Transcript.truncateTo("一二三四五六", 4);
        assertTrue(t.truncated());
        assertEquals("一二三四", t.text());
        Transcript ok = Transcript.truncateTo("一二三四五六", 0);
        assertTrue(!ok.truncated());
        assertEquals("一二三四五六", ok.text());
    }

    // ---- BilibiliVideoSource.isBilibiliUrl ----

    @Test
    void isBilibiliUrl_域名判定() {
        assertTrue(BilibiliVideoSource.isBilibiliUrl("https://www.bilibili.com/video/BV1Rh5S6KEtv/"));
        assertTrue(BilibiliVideoSource.isBilibiliUrl("https://b23.tv/BV1Rh5S6KEtv"));
        assertTrue(!BilibiliVideoSource.isBilibiliUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(!BilibiliVideoSource.isBilibiliUrl(null));
    }

    // ---- parseP（分 P 序号提取） ----

    @Test
    void parseP_各类分P写法() {
        assertEquals(2, BilibiliSubtitleFetcher.parseP(
                "https://www.bilibili.com/video/BV1Rh5S6KEtv?p=2"));
        assertEquals(3, BilibiliSubtitleFetcher.parseP(
                "https://www.bilibili.com/video/BV1Rh5S6KEtv?spm_id_from=333.1&p=3"));
        assertEquals(4, BilibiliSubtitleFetcher.parseP(
                "https://www.bilibili.com/video/BV1Rh5S6KEtv/index_4.html"));
        assertEquals(0, BilibiliSubtitleFetcher.parseP("https://www.bilibili.com/video/BV1Rh5S6KEtv"));
        assertEquals(0, BilibiliSubtitleFetcher.parseP("https://www.bilibili.com/video/BV1Rh5S6KEtv?t=30"));
        assertEquals(0, BilibiliSubtitleFetcher.parseP(null));
    }

    // ---- toVideoInfo / resolveCid（view data → 视频信息，含分 P cid 选择） ----

    private static JsonNode multiPartData() throws Exception {
        return MAPPER.readTree("""
                {
                  "aid": 123456789,
                  "cid": 100,
                  "title": "测试视频",
                  "pic": "http://i0.hdslb.com/bfs/archive/x.jpg",
                  "duration": 3600,
                  "owner": {"name": "UP主昵称"},
                  "pages": [
                    {"cid": 100, "part": "P1"},
                    {"cid": 200, "part": "P2"},
                    {"cid": 300, "part": "P3"}
                  ]
                }
                """);
    }

    @Test
    void toVideoInfo_取指定分P的cid并映射元数据字段() throws Exception {
        var info = BilibiliSubtitleFetcher.toVideoInfo(multiPartData(), 2);
        assertTrue(info.isPresent());
        assertEquals(123456789L, info.get().aid());
        assertEquals(200L, info.get().cid());
        assertEquals("测试视频", info.get().title());
        assertEquals("UP主昵称", info.get().author());
        assertEquals("https://i0.hdslb.com/bfs/archive/x.jpg", info.get().thumbnailUrl());
        assertEquals(3600L, info.get().duration());
    }

    @Test
    void toVideoInfo_无分P或越界回退顶层cid() throws Exception {
        assertEquals(100L, BilibiliSubtitleFetcher.toVideoInfo(multiPartData(), 0).get().cid());
        assertEquals(100L, BilibiliSubtitleFetcher.toVideoInfo(multiPartData(), 5).get().cid());

        JsonNode single = MAPPER.readTree("""
                {"aid": 999, "cid": 777, "title": "单P", "pic": "//i0.hdslb.com/x.jpg", "duration": 120,
                 "owner": {"name": "甲"}}
                """);
        var one = BilibiliSubtitleFetcher.toVideoInfo(single, 0);
        assertTrue(one.isPresent());
        assertEquals(777L, one.get().cid());
        // 协议相对封面也升 https
        assertEquals("https://i0.hdslb.com/x.jpg", one.get().thumbnailUrl());
    }

    @Test
    void toVideoInfo_缺aid或cid返回empty() throws Exception {
        assertTrue(BilibiliSubtitleFetcher.toVideoInfo(
                MAPPER.readTree("{\"cid\": 1, \"title\": \"x\"}"), 0).isEmpty());
        assertTrue(BilibiliSubtitleFetcher.toVideoInfo(MAPPER.readTree("{}"), 0).isEmpty());
        assertTrue(BilibiliSubtitleFetcher.toVideoInfo(null, 0).isEmpty());
    }

    // ---- toMetadata（view 数据 → Metadata） ----

    @Test
    void toMetadata_映射到Metadata且空标题视为无效() throws Exception {
        var info = BilibiliSubtitleFetcher.toVideoInfo(multiPartData(), 1).orElseThrow();
        var meta = BilibiliSubtitleFetcher.toMetadata(info);
        assertTrue(meta.isPresent());
        assertEquals("测试视频", meta.get().title());
        assertEquals("UP主昵称", meta.get().author());
        assertEquals("https://i0.hdslb.com/bfs/archive/x.jpg", meta.get().thumbnailUrl());
        assertEquals(3600L, meta.get().duration());

        assertTrue(BilibiliSubtitleFetcher.toMetadata(null).isEmpty());
    }

    // ---- wbi 签名（期望值由官方文档口径的独立实现算出） ----

    private static final String IMG_KEY = "7cd084941338484aae1ad9425b84077c";
    private static final String SUB_KEY = "4932caff0ff746eab6f01bf08b70ac45";
    private static final String MIXIN_KEY = "ea1db124af3c7062474693fa704f4ff8";

    @Test
    void mixinKey_按乱序表推出32位() {
        assertEquals(MIXIN_KEY, BilibiliSubtitleFetcher.mixinKeyOf(IMG_KEY, SUB_KEY));
    }

    @Test
    void mixinKey_素材不足64位返回空串() {
        assertEquals("", BilibiliSubtitleFetcher.mixinKeyOf(IMG_KEY, null));
        assertEquals("", BilibiliSubtitleFetcher.mixinKeyOf("", ""));
    }

    @Test
    void fileKey_从URL取文件名主体() {
        assertEquals(IMG_KEY, BilibiliSubtitleFetcher.fileKey(
                "https://i0.hdslb.com/bfs/wbi/" + IMG_KEY + ".png"));
        assertEquals("", BilibiliSubtitleFetcher.fileKey(null));
        assertEquals("k", BilibiliSubtitleFetcher.fileKey("k"));
    }

    @Test
    void wbiQuery_单参数与多参数的rid都对齐文档口径() {
        assertEquals("bvid=BV1THe46kEeu&wts=1702204169&w_rid=9e913df63d114e07ba5a29313754d2fd",
                BilibiliSubtitleFetcher.wbiQuery(Map.of("bvid", "BV1THe46kEeu"), MIXIN_KEY, 1702204169L));
        assertEquals("aid=117296211173100&oid=42023259210&type=1&wts=1702204169"
                        + "&w_rid=8255b2e496fd5e4f7d2ceb10f8b72069",
                BilibiliSubtitleFetcher.wbiQuery(Map.of(
                        "aid", "117296211173100", "oid", "42023259210", "type", "1"), MIXIN_KEY, 1702204169L));
    }

    @Test
    void wbiEncode_滤掉特殊字符且空格编成百分号20() {
        assertEquals("a%20b", BilibiliSubtitleFetcher.wbiEncode("a b"));
        assertEquals("Hello", BilibiliSubtitleFetcher.wbiEncode("He!l'l(o)*"));
        assertEquals("BV1THe46kEeu", BilibiliSubtitleFetcher.wbiEncode("BV1THe46kEeu"));
    }
}
