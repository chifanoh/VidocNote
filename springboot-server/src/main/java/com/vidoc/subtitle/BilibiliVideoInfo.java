package com.vidoc.subtitle;

/**
 * B 站 {@code /x/web-interface/view} 解析出的视频信息，同时供元数据与字幕两条链路复用。
 *
 * @param aid          视频 aid（字幕 dm/view 接口所需）
 * @param cid          当前所选分 P 的 cid（分 P 链接按 {@code ?p=N} 从 pages[] 取对；未指明即第一 P）
 * @param title        视频标题
 * @param author       UP 主昵称
 * @param thumbnailUrl 封面地址（已统一为 https）
 * @param duration     时长（秒）；未知为 {@code null}
 */
public record BilibiliVideoInfo(long aid, long cid, String title, String author,
                                String thumbnailUrl, Long duration) {
}
