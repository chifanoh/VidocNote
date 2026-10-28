package com.vidoc.service;

import com.vidoc.config.AgentRagProperties;
import com.vidoc.dto.AgentDtos;
import com.vidoc.entity.SummaryStatus;
import com.vidoc.entity.VideoSummary;
import com.vidoc.exception.ApiException;
import com.vidoc.llm.AgentChatClient;
import com.vidoc.llm.LlmException;
import com.vidoc.repository.VideoSummaryRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Semaphore;

@Service
public class AgentChatService {

    private final VideoSummaryRepository repository;
    private final AgentRagService ragService;
    private final AgentChatClient chatClient;
    private final Semaphore concurrency;
    private final int maxQuestionChars;

    public AgentChatService(VideoSummaryRepository repository,
                            AgentRagService ragService,
                            AgentChatClient chatClient, AgentRagProperties properties) {
        this.repository = repository;
        this.ragService = ragService;
        this.chatClient = chatClient;
        this.concurrency = new Semaphore(Math.max(properties.getConcurrency(), 1), true);
        this.maxQuestionChars = Math.max(properties.getMaxQuestionChars(), 1);
    }

    /**
     * 智能问答服务线程
     * @param userId
     * @param summaryId
     * @param question
     * @return
     */
    public AgentDtos.ChatResponse ask(Long userId, String summaryId, String question) {
        // 鉴权与状态校验
        VideoSummary summary = repository.findByPublicIdAndUserId(summaryId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "摘要记录不存在"));
        if (summary.getStatus() != SummaryStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "摘要尚未生成完成");
        }

        // 限流与削峰
        String trimmed = question.trim();
        if (trimmed.length() > maxQuestionChars) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "问题不能超过 " + maxQuestionChars + " 个字符");
        }
        if (!concurrency.tryAcquire()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 问答当前繁忙，请稍后重试");
        }
        try {
            // 查缓存转向量检索
            List<TranscriptChunk> chunks = ragService.retrieve(summary, trimmed);
            if (chunks.isEmpty()) {
                return new AgentDtos.ChatResponse("根据当前视频内容无法确定。");
            }

            // 拼 Prompt 调大模型
            return new AgentDtos.ChatResponse(chatClient.answer(summary.getTitle(), chunks, trimmed));
        } catch (LlmException e) {
            throw new ApiException(e.getStatus(), e.getMessage());
        } catch (DataAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "问答缓存暂时不可用，请稍后重试");
        } finally {
            concurrency.release();
        }
    }
}
