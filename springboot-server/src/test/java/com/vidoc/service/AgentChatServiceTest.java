package com.vidoc.service;

import com.vidoc.config.AgentRagProperties;
import com.vidoc.dto.AgentDtos;
import com.vidoc.entity.SummaryStatus;
import com.vidoc.entity.VideoSummary;
import com.vidoc.exception.ApiException;
import com.vidoc.llm.AgentChatClient;
import com.vidoc.repository.VideoSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatServiceTest {

    private VideoSummaryRepository repository;
    private AgentRagService ragService;
    private AgentChatClient chatClient;
    private AgentChatService service;
    private VideoSummary summary;

    @BeforeEach
    void setUp() {
        repository = mock(VideoSummaryRepository.class);
        ragService = mock(AgentRagService.class);
        chatClient = mock(AgentChatClient.class);
        service = new AgentChatService(repository, ragService, chatClient, new AgentRagProperties());
        summary = new VideoSummary(7L, "https://example.com/video");
        summary.setTitle("标题");
        summary.setStatus(SummaryStatus.COMPLETED);
        when(repository.findByPublicIdAndUserId(summary.getPublicId(), 7L)).thenReturn(Optional.of(summary));
        when(ragService.retrieve(summary, "问题")).thenReturn(List.of(new TranscriptChunk(0, "证据")));
        when(chatClient.answer("标题", List.of(new TranscriptChunk(0, "证据")), "问题")).thenReturn("回答");
    }

    @Test
    void answersWithRetrievedChunks() {
        AgentDtos.ChatResponse response = service.ask(7L, summary.getPublicId(), " 问题 ");

        assertThat(response.answer()).isEqualTo("回答");
    }

    @Test
    void rejectsAnotherUsersOrMissingSummary() {
        assertThatThrownBy(() -> service.ask(8L, summary.getPublicId(), "问题"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(ragService, never()).retrieve(any(), anyString());
    }

    @Test
    void rejectsIncompleteSummary() {
        summary.setStatus(SummaryStatus.PROCESSING);

        assertThatThrownBy(() -> service.ask(7L, summary.getPublicId(), "问题"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsAnOverlongQuestion() {
        assertThatThrownBy(() -> service.ask(7L, summary.getPublicId(), "问".repeat(1201)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(ragService, never()).retrieve(any(), anyString());
    }
}
