package org.example.sublearn.admin;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AdminAuditAction;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.ProcessingJob;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.job.ProcessingJobResponse;
import org.example.sublearn.job.ProcessingJobService;
import org.example.sublearn.job.RabbitMqConfig;
import org.example.sublearn.repository.ProcessingJobRepository;
import org.example.sublearn.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminJobServiceTest {

    private ProcessingJobRepository jobRepo;
    private VideoRepository videoRepo;
    private ProcessingJobService processingJobService;
    private AdminHealthService healthService;
    private AdminAuditService auditService;
    private AmqpAdmin amqpAdmin;
    private AdminJobService adminJobService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jobRepo = mock(ProcessingJobRepository.class);
        videoRepo = mock(VideoRepository.class);
        processingJobService = mock(ProcessingJobService.class);
        healthService = mock(AdminHealthService.class);
        auditService = mock(AdminAuditService.class);
        amqpAdmin = mock(AmqpAdmin.class);

        ObjectProvider<AmqpAdmin> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(amqpAdmin);

        adminJobService = new AdminJobService(
                jobRepo, videoRepo, processingJobService, healthService, auditService, provider, 2);
    }

    @Test
    void retryJobRejectsNonFailedAndRetriesFailedJobWithAudit() {
        AppUser user = new AppUser("u@example.com", "h", "U");
        Video video = new Video("yt1", "https://youtu.be/yt1", user);
        ReflectionTestUtils.setField(video, "id", 10L);

        ProcessingJob processingJob = new ProcessingJob(video, user, JobType.INGEST_VIDEO, "en", "vi", 5);
        processingJob.markProcessing();
        when(jobRepo.findById(100L)).thenReturn(Optional.of(processingJob));

        ApiException ex = assertThrows(ApiException.class,
                () -> adminJobService.retryJob(100L, 1L, "127.0.0.1"));
        assertEquals("JOB_NOT_RETRYABLE", ex.getCode());

        ProcessingJob failedJob = new ProcessingJob(video, user, JobType.INGEST_VIDEO, "en", "vi", 5);
        ReflectionTestUtils.setField(failedJob, "id", 101L);
        failedJob.markFailed(FailureReason.TRANSLATION_FAILED, "429");
        when(jobRepo.findById(101L)).thenReturn(Optional.of(failedJob));
        when(processingJobService.retryJob(101L, 1L)).thenReturn(new ProcessingJobResponse(
                201L, 10L, JobType.INGEST_VIDEO, JobStatus.PENDING, "en", "vi", 0, 5, 0, null, null));

        var created = adminJobService.retryJob(101L, 1L, "127.0.0.1");
        assertEquals(201L, created.jobId());
        verify(auditService).record(
                eq(1L), eq(AdminAuditAction.RETRY_JOB), eq("JOB"), eq("101"),
                any(), any(), eq("127.0.0.1"));
    }

    @Test
    void bulkRetryEnforcesMaxBatchAndSkipsNonFailed() {
        ApiException limitEx = assertThrows(ApiException.class,
                () -> adminJobService.bulkRetry(List.of(1L, 2L, 3L), 1L, null));
        assertEquals("BULK_RETRY_LIMIT_EXCEEDED", limitEx.getCode());

        AppUser user = new AppUser("u@example.com", "h", "U");
        Video video = new Video("yt1", "https://youtu.be/yt1", user);
        ReflectionTestUtils.setField(video, "id", 10L);

        ProcessingJob failedJob = new ProcessingJob(video, user, JobType.TRANSLATE_SUBTITLES, "en", "vi", 5);
        failedJob.markFailed(FailureReason.TRANSLATION_FAILED, "err");
        ProcessingJob completedJob = new ProcessingJob(video, user, JobType.TRANSLATE_SUBTITLES, "en", "vi", 5);
        completedJob.markCompleted();

        when(jobRepo.findById(10L)).thenReturn(Optional.of(failedJob));
        when(jobRepo.findById(11L)).thenReturn(Optional.of(completedJob));
        when(processingJobService.retryJob(10L, 1L)).thenReturn(new ProcessingJobResponse(
                99L, 10L, JobType.TRANSLATE_SUBTITLES, JobStatus.PENDING, "en", "vi", 0, 5, 0, null, null));

        var response = adminJobService.bulkRetry(List.of(10L, 11L), 1L, "127.0.0.1");
        assertEquals(2, response.requestedCount());
        assertEquals(1, response.retriedCount());
        assertEquals(List.of(11L), response.skippedIds());
        assertEquals(List.of(99L), response.createdJobIds());
    }

    @Test
    void forceFailUpdatesPendingIngestJobAndVideoAndPurgeDlqCallsAmqpAdmin() {
        AppUser user = new AppUser("u@example.com", "h", "U");
        Video video = new Video("yt1", "https://youtu.be/yt1", user);
        video.setStatus(VideoStatus.PROCESSING);
        ReflectionTestUtils.setField(video, "id", 10L);

        ProcessingJob activeJob = new ProcessingJob(video, user, JobType.INGEST_VIDEO, "en", "vi", 5);
        ReflectionTestUtils.setField(activeJob, "id", 50L);
        activeJob.markProcessing();
        when(jobRepo.findById(50L)).thenReturn(Optional.of(activeJob));

        var dto = adminJobService.forceFailJob(50L, "Stuck worker", 1L, "127.0.0.1");
        assertEquals(JobStatus.FAILED, dto.status());
        assertEquals(VideoStatus.FAILED, video.getStatus());
        verify(videoRepo).save(video);

        when(amqpAdmin.purgeQueue(RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ)).thenReturn(4);
        var purge = adminJobService.purgeDlq(1L, "127.0.0.1");
        assertEquals(4, purge.purgedCount());
        verify(auditService).record(
                eq(1L), eq(AdminAuditAction.PURGE_DLQ), eq("QUEUE"),
                eq(RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ), any(), any(), eq("127.0.0.1"));
    }
}
