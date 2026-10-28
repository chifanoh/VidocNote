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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 异步摘要流水线：PENDING → PROCESSING → COMPLETED。
 * 编排：选视频源适配器 → 元数据 → 字幕 → LLM 生成 → 落库。
 *
 * <p>失败不落库：任何一步失败都直接删除该记录（原因只写服务端日志），库里只保留可用的完成记录，
 * 历史列表因此永远只出现已完成的任务。进行中的记录会在库里短暂存在（异步处理与前端轮询需要），
 * 但列表接口按 COMPLETED 过滤，用户看不到。
 */
@Service
public class SummaryProcessor {

    private static final Logger log = LoggerFactory.getLogger(SummaryProcessor.class);

    static final String STEP_FETCH_METADATA = "FETCH_METADATA";
    static final String STEP_FETCH_TRANSCRIPT = "FETCH_TRANSCRIPT";
    static final String STEP_SUMMARIZE = "SUMMARIZE";

    private final VideoSummaryRepository repository;
    private final VideoSourceRegistry videoSourceRegistry;
    private final LlmProvider llmProvider;
    private final LlmProperties llmProperties;
    private final AgentRagProperties agentRagProperties;
    private final AgentTranscriptCache agentTranscriptCache;
    private final PointsService pointsService;
    private final RejectionLog rejections;
    /** 单条视频时长上限（秒）。超了直接拒绝：长视频占池子久、成本高，字幕也会被截断 */
    private final long maxDurationSeconds;

    public SummaryProcessor(VideoSummaryRepository repository, VideoSourceRegistry videoSourceRegistry,
                            LlmProvider llmProvider, LlmProperties llmProperties,
                            AgentRagProperties agentRagProperties,
                            AgentTranscriptCache agentTranscriptCache,
                            PointsService pointsService, RejectionLog rejections,
                            @Value("${vidoc.summary.max-duration-seconds:10800}") long maxDurationSeconds) {
        this.repository = repository;
        this.videoSourceRegistry = videoSourceRegistry;
        this.llmProvider = llmProvider;
        this.llmProperties = llmProperties;
        this.agentRagProperties = agentRagProperties;
        this.agentTranscriptCache = agentTranscriptCache;
        this.pointsService = pointsService;
        this.rejections = rejections;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    /**
     * 执行一次摘要任务。任何一步失败都丢弃记录（不置 FAILED、不写 errorMessage）。
     * 扣分在拿到视频时长之后进行（提交时还不知道时长）；扣分之后的失败必须原额退回，且按原分项退回。
     */
    public void process(Long summaryId) {
        VideoSummary summary = repository.findById(summaryId).orElse(null);
        if (summary == null) {
            return;
        }
        summary.setStatus(SummaryStatus.PROCESSING);
        summary.setProgressStep(STEP_FETCH_METADATA);
        repository.save(summary);

        UserPoints.Charge charged = UserPoints.Charge.NONE;
        boolean metadataDone = false;
        Long durationSeconds = null;
        long startedAt = System.currentTimeMillis();
        try {
            String videoUrl = summary.getVideoUrl();
            // 平台判断只此一处，主流程不认识任何具体平台
            VideoSource source;
            try {
                source = videoSourceRegistry.resolve(videoUrl);
            } catch (YtDlpException e) {
                // 认不出链接是用户输入问题，原因可以原样告诉他
                log.warn("摘要任务 {} 链接不受支持，记录已丢弃：{}", summaryId, videoUrl);
                reject(summary, charged, "链接无效或暂不支持该平台");
                return;
            }

            Optional<VideoSource.Metadata> found = source.metadata(videoUrl);
            if (found.isEmpty()) {
                log.warn("摘要任务 {} 无法解析视频信息，记录已丢弃：{}", summaryId, videoUrl);
                reject(summary, charged, "无法解析该视频，请确认链接有效或视频未被删除");
                return;
            }
            VideoSource.Metadata metadata = found.get();
            metadataDone = true;
            summary.setTitle(metadata.title());
            summary.setAuthor(metadata.author());
            summary.setThumbnailUrl(metadata.thumbnailUrl());
            summary.setDuration(metadata.duration());
            durationSeconds = metadata.duration();

            // 时长上限：在扣分之前拦掉，用户一分不损（拿不到时长就不拦，交给后面正常流程）
            if (metadata.duration() != null && metadata.duration() > maxDurationSeconds) {
                log.warn("摘要任务 {} 视频时长约 {} 分钟，超过上限 {} 分钟，记录已丢弃：{}",
                        summaryId, (metadata.duration() + 59) / 60, maxDurationSeconds / 60, videoUrl);
                reject(summary, charged, "视频时长超过 " + durationLimitLabel() + "上限，暂不支持");
                return;
            }

            int cost = PointsService.costOf(metadata.duration());
            UserPoints.Charge charge = pointsService.consume(summary.getUserId(), cost, summaryId);
            if (charge == null) {
                log.warn("摘要任务 {} 积分不足（需 {} 分），记录已丢弃", summaryId, cost);
                reject(summary, charged, "积分不足：这条视频需要 " + cost + " 分");
                return;
            }
            charged = charge;

            summary.setProgressStep(STEP_FETCH_TRANSCRIPT);
            repository.save(summary);

            Optional<VideoSource.Transcript> subtitles =
                    source.transcript(videoUrl, agentRagProperties.getMaxTranscriptChars());
            if (subtitles.isEmpty()) {
                log.warn("摘要任务 {} 无可用字幕，记录已丢弃：{}", summaryId, videoUrl);
                reject(summary, charged, "该视频没有可用字幕，暂不支持生成摘要");
                return;
            }
            VideoSource.Transcript transcript = subtitles.get();
            VideoSource.Transcript summaryTranscript =
                    VideoSource.Transcript.truncateTo(transcript.text(), llmProperties.getMaxInputChars());

            summary.setProgressStep(STEP_SUMMARIZE);
            repository.save(summary);

            LlmProvider.Result result = llmProvider.summarize(summaryTranscript.text());
            summary.setSummaryText(result.text());
            summary.setTruncated(transcript.truncated() || summaryTranscript.truncated() || result.truncated());
            summary.setStatus(SummaryStatus.COMPLETED);
            summary.setProgressStep(null);
            cacheTranscript(summary, transcript);
            if (result.truncated()) {
                // 撞上限的正文是半截，但这批 token 已经付了：入库 + 标记，不退分（丢弃才是白烧）
                log.warn("摘要任务 {} 输出被 max_tokens 截断（{}），按已生成内容入库", summaryId, modelName());
            }
        } catch (YtDlpException e) {
            log.warn("摘要任务 {} 失败（{}），记录已丢弃：{}", summaryId, modelName(), e.getMessage());
            // 元数据都没拿到 → 链接问题；拿到了 → 是抓字幕那一趟失败，两者对用户不是一回事
            reject(summary, charged, metadataDone
                    ? "字幕抓取失败，请稍后重试"
                    : "无法解析该视频，请确认链接有效或视频未被删除");
            return;
        } catch (LlmException e) {
            log.warn("摘要任务 {} LLM 失败（{}），记录已丢弃：{}", summaryId, modelName(), e.getMessage());
            reject(summary, charged, "AI 生成失败，请稍后重试");
            return;
        } catch (Exception e) {
            log.error("摘要任务 {} 未知错误（{}），记录已丢弃", summaryId, modelName(), e);
            reject(summary, charged, "生成失败，请稍后重试");
            return;
        }
        // 失败分支都已 return 并丢弃记录，走到这里即成功
        repository.save(summary);
        log.info("摘要任务 {} 完成（{}），正文 {} 字符，视频 {} 秒，扣 {} 分，耗时 {} ms",
                summaryId, modelName(), summary.getSummaryText().length(), durationSeconds,
                charged.total(), System.currentTimeMillis() - startedAt);
    }

    /** 每次生成都在日志里说清是哪个模型出的 */
    private String modelName() {
        String model = llmProperties.getModel();
        return model == null || model.isBlank() ? "模型未配置" : model;
    }

    private void cacheTranscript(VideoSummary summary, VideoSource.Transcript transcript) {
        try {
            agentTranscriptCache.put(summary.getPublicId(), transcript.text(), transcript.truncated());
        } catch (RuntimeException e) {
            // 字幕缓存只是问答加速层；Redis 不可用不能影响已经生成的摘要。
            log.warn("摘要任务 {} 的字幕缓存写入失败：{}", summary.getId(), e.getMessage());
        }
    }

    /** 上限的中文说法：整小时说小时、整分钟说分钟，否则说秒 —— 上限可配，不能把「3 小时」写死 */
    private String durationLimitLabel() {
        if (maxDurationSeconds % 3600 == 0) {
            return maxDurationSeconds / 3600 + " 小时";
        }
        return maxDurationSeconds % 60 == 0 ? maxDurationSeconds / 60 + " 分钟" : maxDurationSeconds + " 秒";
    }

    /**
     * 丢弃失败记录，并把已扣的积分按原分项退回（未扣则只删记录）。
     * 原因同时登记到 {@link RejectionLog}：记录删了之后查询接口只剩 404，用户就无从知道为什么失败。
     */
    private void reject(VideoSummary summary, UserPoints.Charge charged, String reason) {
        rejections.put(summary.getPublicId(), reason);
        pointsService.refund(summary.getUserId(), charged, summary.getId());
        repository.delete(summary);
    }
}
