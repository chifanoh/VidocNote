package com.vidoc.video;

import com.vidoc.ytdlp.YtDlpException;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台判断的唯一入口：主流程只管问它要一个适配器，不自己认域名。
 * 适配器由 Spring 注入，按 {@code @Order} 升序取第一个认领的（通用兜底源须排最后）。
 */
@Component
public class VideoSourceRegistry {

    private final List<VideoSource> sources;

    public VideoSourceRegistry(List<VideoSource> sources) {
        // 排序认 @Order，故与注入顺序无关
        this.sources = new ArrayList<>(sources);
        AnnotationAwareOrderComparator.sort(this.sources);
    }

    /** 无适配器认领即视为不受支持；调用方按失败处理。 */
    public VideoSource resolve(String url) {
        return sources.stream()
                .filter(source -> source.supports(url))
                .findFirst()
                .orElseThrow(() -> new YtDlpException("无法识别的视频链接（链接无效或暂不支持该平台）"));
    }

    /** 已注册的适配器（按 @Order），测试与诊断用。 */
    public List<VideoSource> sources() {
        return List.copyOf(sources);
    }
}
