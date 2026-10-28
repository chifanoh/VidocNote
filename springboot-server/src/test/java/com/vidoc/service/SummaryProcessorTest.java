package com.vidoc.service;

import com.vidoc.config.LlmProperties;
import com.vidoc.config.AgentRagProperties;
import com.vidoc.entity.SummaryStatus;
import com.vidoc.entity.UserPoints;
import com.vidoc.entity.VideoSummary;
import com.vidoc.llm.LlmException;
import com.vidoc.llm.LlmProvider;
import com.vidoc.repository.VideoSummaryRepository;
import com.vidoc.video.VideoSource;
import com.vidoc.video.VideoSourceRegistry;
import com.vidoc.ytdlp.YtDlpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryProcessorTest {

    private static final Long USER = 7L;
    private static final Long SUMMARY_ID = 42L;
    private static final String URL = "https://www.bilibili.com/video/BV1xx411c7mD";
    private static final long MAX_DURATION = 10800;

    private VideoSummaryRepository repository;
    private VideoSourceRegistry registry;
    private VideoSource source;
    private LlmProvider llmProvider;
    private AgentTranscriptCache agentTranscriptCache;
    private PointsService pointsService;
    private RejectionLog rejections;
    private SummaryProcessor processor;

    @BeforeEach
    void setUp() {
        repository = mock(VideoSummaryRepository.class);
        registry = mock(VideoSourceRegistry.class);
        source = mock(VideoSource.class);
        llmProvider = mock(LlmProvider.class);
        agentTranscriptCache = mock(AgentTranscriptCache.class);
        pointsService = mock(PointsService.class);
        rejections = new RejectionLog();

        when(registry.resolve(URL)).thenReturn(source);
        when(source.metadata(URL)).thenReturn(Optional.of(metadata(600L)));
        when(source.transcript(eq(URL), anyLong())).thenReturn(Optional.of(new VideoSource.Transcript("字幕", false)));

        processor = new SummaryProcessor(repository, registry, llmProvider, new LlmProperties(),
                new AgentRagProperties(), agentTranscriptCache, pointsService, rejections, MAX_DURATION);
    }

    private static VideoSource.Metadata metadata(Long duration) {
        return new VideoSource.Metadata("标题", "作者", "https://img", duration);
    }

    /** 真实流程里这行是先 save 过再进处理器的，id 必须补上 —— 退回是按 id 关联的 */
    private VideoSummary existing() {
        VideoSummary summary = new VideoSummary(USER, URL);
        ReflectionTestUtils.setField(summary, "id", SUMMARY_ID);
        when(repository.findById(SUMMARY_ID)).thenReturn(Optional.of(summary));
        return summary;
    }

    private String reasonOf(VideoSummary summary) {
        return rejections.reasonOf(summary.getPublicId()).orElse(null);
    }

    // ---- 拒绝：每一种都要给用户一句能看懂的话 ----

    @Test
    void aVideoOverTheDurationLimitIsRejectedBeforeCharging() {
        VideoSummary summary = existing();
        when(source.metadata(URL)).thenReturn(Optional.of(metadata(MAX_DURATION + 1)));

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("3 小时");
        verify(pointsService, never()).consume(anyLong(), anyInt(), anyLong());
        verify(llmProvider, never()).summarize(anyString());
        verify(repository).delete(any());
    }

    @Test
    void aMissingTranscriptTellsTheUserAboutIt() {
        VideoSummary summary = existing();
        when(source.transcript(eq(URL), anyLong())).thenReturn(Optional.empty());
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(new UserPoints.Charge(0, 10));

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("字幕");
        verify(pointsService).refund(USER, new UserPoints.Charge(0, 10), SUMMARY_ID);
        verify(repository).delete(any());
    }

    @Test
    void anUnresolvableVideoTellsTheUserAboutIt() {
        VideoSummary summary = existing();
        when(source.metadata(URL)).thenReturn(Optional.empty());

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("无法解析");
        verify(pointsService, never()).consume(anyLong(), anyInt(), anyLong());
        verify(repository).delete(any());
    }

    /** 链接无效 / 平台不支持：连适配器都找不到，在扣分之前就拒掉 */
    @Test
    void anUnsupportedLinkTellsTheUserAboutIt() {
        VideoSummary summary = existing();
        when(registry.resolve(URL)).thenThrow(new YtDlpException("无法识别的视频链接（链接无效或暂不支持该平台）"));

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("链接无效");
        verify(pointsService, never()).consume(anyLong(), anyInt(), anyLong());
        verify(repository).delete(any());
    }

    @Test
    void anInsufficientBalanceAtChargeTimeTellsTheUserAboutIt() {
        VideoSummary summary = existing();
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(null);

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("积分不足");
        verify(llmProvider, never()).summarize(anyString());
        verify(repository).delete(any());
    }

    @Test
    void aFailingLlmCallRefundsAndTellsTheUserAboutIt() {
        VideoSummary summary = existing();
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(new UserPoints.Charge(0, 10));
        when(llmProvider.summarize(anyString())).thenThrow(new LlmException("上游 500"));

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("AI 生成失败");
        verify(pointsService).refund(USER, new UserPoints.Charge(0, 10), SUMMARY_ID);
        verify(repository).delete(any());
    }

    /** 元数据都没拿到 ⇒ 是链接的问题 */
    @Test
    void aFailingMetadataCallBlamesTheLink() {
        VideoSummary summary = existing();
        when(source.metadata(URL)).thenThrow(new YtDlpException("yt-dlp 执行失败（退出码 1）：ERROR: 视频不存在"));

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("无法解析该视频");
        verify(repository).delete(any());
    }

    /** 元数据拿到了、倒在抓字幕那一趟 ⇒ 不是链接的问题，别让用户去改链接 */
    @Test
    void aFailingTranscriptCallDoesNotBlameTheLink() {
        VideoSummary summary = existing();
        when(source.transcript(eq(URL), anyLong())).thenThrow(new YtDlpException("yt-dlp 执行超时（>120 秒）"));
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(new UserPoints.Charge(0, 10));

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).contains("字幕抓取失败");
        verify(pointsService).refund(USER, new UserPoints.Charge(0, 10), SUMMARY_ID);
        verify(repository).delete(any());
    }

    // ---- 正常路径 ----

    /** 恰好卡在上限的视频要放行（边界取「不超过」） */
    @Test
    void aVideoExactlyAtTheLimitIsAccepted() {
        existing();
        when(source.metadata(URL)).thenReturn(Optional.of(metadata(MAX_DURATION)));
        when(pointsService.consume(USER, PointsService.costOf(MAX_DURATION), SUMMARY_ID))
                .thenReturn(UserPoints.Charge.NONE);
        when(llmProvider.summarize(anyString())).thenReturn(new LlmProvider.Result("正文", false));

        processor.process(SUMMARY_ID);

        verify(pointsService).consume(USER, PointsService.costOf(MAX_DURATION), SUMMARY_ID);
        verify(llmProvider).summarize(anyString());
        verify(repository, never()).delete(any());
    }

    @Test
    void aSuccessfulRunIsCompletedAndChargedWithoutRegisteringAReason() {
        VideoSummary summary = existing();
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(new UserPoints.Charge(0, 10));
        when(llmProvider.summarize(anyString())).thenReturn(new LlmProvider.Result("正文", false));

        processor.process(SUMMARY_ID);

        assertThat(summary.getStatus()).isEqualTo(SummaryStatus.COMPLETED);
        assertThat(summary.getSummaryText()).isEqualTo("正文");
        assertThat(summary.getProgressStep()).isNull();
        assertThat(reasonOf(summary)).isNull();
        verify(pointsService, never()).refund(anyLong(), any(), anyLong());
        verify(agentTranscriptCache).put(anyString(), eq("字幕"), eq(false));
    }

    @Test
    void theFullTranscriptIsCachedWhileOnlyTheSummaryInputIsTruncated() {
        existing();
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setMaxInputChars(2);
        processor = new SummaryProcessor(repository, registry, llmProvider, llmProperties,
                new AgentRagProperties(), agentTranscriptCache, pointsService, rejections, MAX_DURATION);
        when(source.transcript(eq(URL), anyLong()))
                .thenReturn(Optional.of(new VideoSource.Transcript("完整字幕内容", false)));
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(new UserPoints.Charge(0, 10));
        when(llmProvider.summarize(anyString())).thenReturn(new LlmProvider.Result("正文", false));

        processor.process(SUMMARY_ID);

        verify(llmProvider).summarize("完整");
        verify(agentTranscriptCache).put(anyString(), eq("完整字幕内容"), eq(false));
    }

    /** 被 max_tokens 截断不算失败：正文照常入库、标记截断、不退分 */
    @Test
    void aTruncatedAnswerIsKeptAndFlagsTruncated() {
        VideoSummary summary = existing();
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(new UserPoints.Charge(0, 10));
        when(llmProvider.summarize(anyString())).thenReturn(new LlmProvider.Result("半截正文", true));

        processor.process(SUMMARY_ID);

        assertThat(summary.getStatus()).isEqualTo(SummaryStatus.COMPLETED);
        assertThat(summary.isTruncated()).isTrue();
        assertThat(reasonOf(summary)).isNull();
        verify(pointsService, never()).refund(anyLong(), any(), anyLong());
        verify(repository, never()).delete(any());
    }

    /** 记录已被别处删掉（重启清理/用户删除）时直接返回，不做任何事 */
    @Test
    void aMissingRecordIsIgnored() {
        when(repository.findById(SUMMARY_ID)).thenReturn(Optional.empty());

        processor.process(SUMMARY_ID);

        verify(pointsService, never()).consume(anyLong(), anyInt(), anyLong());
        verify(repository, never()).delete(any());
    }

    /** 拿不到时长（比如平台没给）不能拦，交给后面正常流程 */
    @Test
    void anUnknownDurationIsNotRejected() {
        VideoSummary summary = existing();
        when(source.metadata(URL)).thenReturn(Optional.of(metadata(null)));
        when(pointsService.consume(USER, PointsService.MIN_COST, SUMMARY_ID))
                .thenReturn(UserPoints.Charge.NONE);
        when(llmProvider.summarize(anyString())).thenReturn(new LlmProvider.Result("正文", false));

        processor.process(SUMMARY_ID);

        assertThat(reasonOf(summary)).isNull();
        verify(llmProvider).summarize(anyString());
        verify(repository, never()).delete(any());
    }

    @Test
    void aTranscriptCacheFailureDoesNotFailTheSummary() {
        VideoSummary summary = existing();
        when(pointsService.consume(USER, 10, SUMMARY_ID)).thenReturn(new UserPoints.Charge(0, 10));
        when(llmProvider.summarize(anyString())).thenReturn(new LlmProvider.Result("正文", false));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(agentTranscriptCache).put(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());

        processor.process(SUMMARY_ID);

        assertThat(summary.getStatus()).isEqualTo(SummaryStatus.COMPLETED);
        verify(repository, never()).delete(any());
    }
}
