package com.vidoc.service;

import com.vidoc.dto.SummaryDtos;
import com.vidoc.entity.SummaryStatus;
import com.vidoc.entity.VideoSummary;
import com.vidoc.exception.ApiException;
import com.vidoc.repository.VideoSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SummaryServiceTest {

    private static final Long USER = 7L;
    private static final String PUBLIC_ID = "0192f0aa-0000-7000-8000-000000000000";
    private static final SummaryDtos.CreateSummaryRequest REQUEST =
            new SummaryDtos.CreateSummaryRequest("https://www.bilibili.com/video/BV1xx411c7mD");
    private static final String NO_SUBTITLE = "该视频没有可用字幕，暂不支持生成摘要";

    private VideoSummaryRepository repository;
    private SummaryProcessor processor;
    private PointsService pointsService;
    private RejectionLog rejections;
    private Executor summaryExecutor;
    private SummaryService service;

    @BeforeEach
    void setUp() {
        repository = mock(VideoSummaryRepository.class);
        processor = mock(SummaryProcessor.class);
        pointsService = mock(PointsService.class);
        rejections = mock(RejectionLog.class);
        summaryExecutor = mock(Executor.class);
        service = new SummaryService(repository, processor, pointsService, rejections, summaryExecutor);
        when(pointsService.balance(USER)).thenReturn(100);
        // save 默认返回 null，后面 created.getId() 会直接 NPE
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSubmitsToThePool() {
        service.create(USER, REQUEST);

        verify(summaryExecutor).execute(any());
    }

    @Test
    void aFullPoolDropsTheRecordAndReports503() {
        doThrow(new TaskRejectedException("full")).when(summaryExecutor).execute(any());

        assertThatThrownBy(() -> service.create(USER, REQUEST))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(repository).delete(any());
    }

    @Test
    void insufficientBalanceCreatesNothing() {
        when(pointsService.balance(USER)).thenReturn(PointsService.MIN_COST - 1);

        assertThatThrownBy(() -> service.create(USER, REQUEST))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));
        verify(repository, never()).save(any());
        verifyNoInteractions(summaryExecutor);
    }

    // ---- 失败原因的回传 ----

    /** 记录已删（失败不落库），但登记处还留着原因：返回一条 FAILED 把原因带给用户，而不是 404 */
    @Test
    void aDeletedRecordWithAKnownReasonComesBackAsFailedWithTheReason() {
        when(repository.findByPublicIdAndUserId(PUBLIC_ID, USER)).thenReturn(Optional.empty());
        when(rejections.reasonOf(PUBLIC_ID)).thenReturn(Optional.of(NO_SUBTITLE));

        SummaryDtos.SummaryResponse response = service.getOwned(USER, PUBLIC_ID);

        assertThat(response.status()).isEqualTo(SummaryStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo(NO_SUBTITLE);
        assertThat(response.id()).isEqualTo(PUBLIC_ID);
    }

    /** 原因过期/从未失败过：还是 404，不凭空造记录 */
    @Test
    void anUnknownRecordStays404() {
        when(repository.findByPublicIdAndUserId(PUBLIC_ID, USER)).thenReturn(Optional.empty());
        when(rejections.reasonOf(PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(USER, PUBLIC_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void anExistingRecordIsReturnedAsCompleted() {
        VideoSummary found = new VideoSummary(USER, REQUEST.videoUrl());
        found.setStatus(SummaryStatus.COMPLETED);
        when(repository.findByPublicIdAndUserId(PUBLIC_ID, USER)).thenReturn(Optional.of(found));

        SummaryDtos.SummaryResponse response = service.getOwned(USER, PUBLIC_ID);

        assertThat(response.status()).isEqualTo(SummaryStatus.COMPLETED);
        verify(rejections, never()).reasonOf(any());
    }
}
