package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 视频问答的临时数据与检索参数。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "vidoc.agent.rag")
public class AgentRagProperties {

    /** 完整字幕只作为缓存保存，不写入 MySQL。 */
    private Duration transcriptTtl = Duration.ofHours(24);

    private Duration vectorTtl = Duration.ofDays(7);

    private int chunkSize = 700;

    private int chunkOverlap = 80;

    private int topK = 3;

    /** 0 表示不额外截断，仍受视频时长上限约束。 */
    private long maxTranscriptChars = 0;

    private int maxQuestionChars = 1200;

    private int concurrency = 4;
}
