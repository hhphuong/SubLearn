package org.example.sublearn.job;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.ProcessingJob;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.ingestion.VideoProgressTracker;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.ProcessingJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessingJobServiceTest {

    @Test
    void retryJobKeepsFailedJobForAuditAndCreatesNewPendingJob() {
        ProcessingJobRepository jobRepo = mock(ProcessingJobRepository.class);
        AppUserRepository userRepo = mock(AppUserRepository.class);
        VideoJobProducer producer = mock(VideoJobProducer.class);
        VideoProgressTracker tracker = mock(VideoProgressTracker.class);
        ProcessingJobService service = new ProcessingJobService(jobRepo, userRepo, producer, tracker, 5);

        AppUser user = new AppUser("u@example.com", "hash", "User");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(userRepo.getReferenceById(7L)).thenReturn(user);

        Video video = new Video("abc123", "https://youtu.be/abc123", user);
        ReflectionTestUtils.setField(video, "id", 10L);
        video.markFailed(FailureReason.TRANSLATION_FAILED, "quota");

        ProcessingJob failedJob = new ProcessingJob(video, user, JobType.INGEST_VIDEO, "en", "vi", 5);
        ReflectionTestUtils.setField(failedJob, "id", 100L);
        failedJob.markFailed(FailureReason.TRANSLATION_FAILED, "quota");

        when(jobRepo.findById(100L)).thenReturn(Optional.of(failedJob));
        when(jobRepo.findFirstByVideo_IdAndJobTypeInAndTargetLanguageAndStatusInOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(jobRepo.save(any(ProcessingJob.class))).thenAnswer(inv -> {
            ProcessingJob saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });

        ProcessingJobResponse newJob = service.retryJob(100L, 7L);

        // Old job remains FAILED for audit history
        assertEquals(JobStatus.FAILED, failedJob.getStatus());
        assertEquals(FailureReason.TRANSLATION_FAILED, failedJob.getFailureReason());

        // New job is created and enqueued
        assertNotEquals(100L, newJob.jobId());
        assertEquals(101L, newJob.jobId());
        assertEquals(JobStatus.PENDING, newJob.status());
        assertEquals(0, newJob.retryCount());
        assertEquals(VideoStatus.PENDING, video.getStatus());
        verify(producer).publishAfterCommit(101L);
    }
}
