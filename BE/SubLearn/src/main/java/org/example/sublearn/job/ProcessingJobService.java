package org.example.sublearn.job;

import org.example.sublearn.common.ApiException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ProcessingJobService {

    private static final Set<JobStatus> ACTIVE_JOB_STATUSES = EnumSet.of(JobStatus.PENDING, JobStatus.PROCESSING);
    private static final List<JobType> TRANSLATION_JOB_TYPES = List.of(
            JobType.TRANSLATE_SUBTITLES, JobType.RETRANSLATE_SUBTITLES);

    private final ProcessingJobRepository processingJobRepository;
    private final AppUserRepository appUserRepository;
    private final VideoJobProducer videoJobProducer;
    private final VideoProgressTracker progressTracker;
    private final int maxRetries;

    public ProcessingJobService(ProcessingJobRepository processingJobRepository,
                                 AppUserRepository appUserRepository,
                                 VideoJobProducer videoJobProducer,
                                 VideoProgressTracker progressTracker,
                                 @Value("${sublearn.job.max-retries:5}") int maxRetries) {
        this.processingJobRepository = processingJobRepository;
        this.appUserRepository = appUserRepository;
        this.videoJobProducer = videoJobProducer;
        this.progressTracker = progressTracker;
        this.maxRetries = maxRetries;
    }

    @Transactional
    public ProcessingJobResponse createAndEnqueue(Video video, Long userId, JobType jobType,
                                                   String sourceLanguage, String targetLanguage) {
        List<JobType> typesToCheck = jobType == JobType.INGEST_VIDEO
                ? List.of(JobType.INGEST_VIDEO)
                : TRANSLATION_JOB_TYPES;

        Optional<ProcessingJob> existingActive = processingJobRepository
                .findFirstByVideo_IdAndJobTypeInAndTargetLanguageAndStatusInOrderByCreatedAtDesc(
                        video.getId(), typesToCheck, targetLanguage, ACTIVE_JOB_STATUSES);
        if (existingActive.isPresent()) {
            return toResponse(existingActive.get());
        }

        AppUser requestedBy = userId != null ? appUserRepository.getReferenceById(userId) : null;
        ProcessingJob job = new ProcessingJob(video, requestedBy, jobType, sourceLanguage, targetLanguage, maxRetries);
        processingJobRepository.save(job);
        videoJobProducer.publishAfterCommit(job.getId());
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponse getJob(Long jobId) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Processing job not found: " + jobId));
        return toResponse(job);
    }

    @Transactional
    public ProcessingJobResponse retryJob(Long jobId, Long userId) {
        ProcessingJob failedJob = processingJobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Processing job not found: " + jobId));
        if (ACTIVE_JOB_STATUSES.contains(failedJob.getStatus())) {
            return toResponse(failedJob);
        }
        if (failedJob.getStatus() == JobStatus.COMPLETED) {
            return toResponse(failedJob);
        }

        Video video = failedJob.getVideo();
        if (failedJob.getJobType() == JobType.INGEST_VIDEO) {
            video.setStatus(VideoStatus.PENDING);
        }
        // Keep the old FAILED job untouched for audit history and enqueue a brand new job record.
        Long requestedBy = userId != null
                ? userId
                : (failedJob.getRequestedByUser() != null ? failedJob.getRequestedByUser().getId() : null);
        return createAndEnqueue(video, requestedBy, failedJob.getJobType(),
                failedJob.getSourceLanguage(), failedJob.getTargetLanguage());
    }

    @Transactional(readOnly = true)
    public Optional<ProcessingJobResponse> findLatestTranslationJob(Long videoId, String targetLanguage) {
        return processingJobRepository
                .findFirstByVideo_IdAndJobTypeInAndTargetLanguageOrderByCreatedAtDesc(
                        videoId, TRANSLATION_JOB_TYPES, targetLanguage)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<ProcessingJobResponse> findLatestIngestJob(Long videoId) {
        return processingJobRepository
                .findFirstByVideo_IdAndJobTypeOrderByCreatedAtDesc(videoId, JobType.INGEST_VIDEO)
                .map(this::toResponse);
    }

    @Transactional
    public Optional<JobExecutionSnapshot> claimForProcessing(Long jobId) {
        Optional<ProcessingJob> optional = processingJobRepository.findById(jobId);
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        ProcessingJob job = optional.get();
        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
            return Optional.empty();
        }
        job.markProcessing();
        Video video = job.getVideo();
        Long userId = job.getRequestedByUser() != null ? job.getRequestedByUser().getId() : null;
        return Optional.of(new JobExecutionSnapshot(
                job.getId(),
                video.getId(),
                video.getYoutubeVideoId(),
                job.getJobType(),
                job.getSourceLanguage() != null ? job.getSourceLanguage() : video.getSourceLanguage(),
                job.getTargetLanguage(),
                userId,
                job.getRetryCount(),
                job.getMaxRetries()
        ));
    }

    @Transactional
    public void updateProgress(Long jobId, int progressPercent) {
        processingJobRepository.findById(jobId).ifPresent(job -> job.setProgressPercent(progressPercent));
    }

    @Transactional
    public void markCompleted(Long jobId) {
        processingJobRepository.findById(jobId).ifPresent(ProcessingJob::markCompleted);
    }

    @Transactional
    public void recordRetry(Long jobId, FailureReason reason, String message) {
        processingJobRepository.findById(jobId).ifPresent(job -> job.recordRetryAttempt(reason, message));
    }

    @Transactional
    public void markFailed(Long jobId, FailureReason reason, String message) {
        processingJobRepository.findById(jobId).ifPresent(job -> job.markFailed(reason, message));
    }

    private ProcessingJobResponse toResponse(ProcessingJob job) {
        int progress = job.getProgressPercent();
        if (job.getJobType() == JobType.INGEST_VIDEO && job.getStatus() == JobStatus.PROCESSING) {
            progress = progressTracker.liveProgressPercent(job.getVideo().getId())
                    .orElse(Math.max(progress, job.getVideo().getProgressPercent()));
        }
        return new ProcessingJobResponse(
                job.getId(),
                job.getVideo().getId(),
                job.getJobType(),
                job.getStatus(),
                job.getSourceLanguage(),
                job.getTargetLanguage(),
                job.getRetryCount(),
                job.getMaxRetries(),
                progress,
                job.getFailureReason(),
                job.getFailureMessage()
        );
    }

    public record JobExecutionSnapshot(
            Long jobId,
            Long videoId,
            String youtubeVideoId,
            JobType jobType,
            String sourceLanguage,
            String targetLanguage,
            Long requestedByUserId,
            int retryCount,
            int maxRetries
    ) {
    }
}
