package org.example.sublearn.job;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.gemini.GeminiClient;
import org.example.sublearn.gemini.GeminiUnavailableException;
import org.example.sublearn.ingestion.VideoIngestionPersister;
import org.example.sublearn.ingestion.VideoProgressTracker;
import org.example.sublearn.ingestion.VttCue;
import org.example.sublearn.ingestion.VttParser;
import org.example.sublearn.ingestion.YtDlpClient;
import org.example.sublearn.ingestion.YtDlpProcessException;
import org.example.sublearn.ingestion.YtDlpResult;
import org.example.sublearn.nlp.TokenizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class VideoJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(VideoJobConsumer.class);
    static final int TRANSLATION_BATCH_SIZE = 50;

    private static final Set<FailureReason> NON_RETRYABLE_REASONS = EnumSet.of(
            FailureReason.INVALID_URL,
            FailureReason.VIDEO_UNAVAILABLE,
            FailureReason.VIDEO_PRIVATE,
            FailureReason.VIDEO_REGION_LOCKED,
            FailureReason.VIDEO_AGE_RESTRICTED,
            FailureReason.NO_CAPTIONS_AVAILABLE,
            FailureReason.SOURCE_LANGUAGE_UNDETECTABLE
    );

    private final ProcessingJobService jobService;
    private final VideoJobProducer jobProducer;
    private final YtDlpClient ytDlpClient;
    private final VideoIngestionPersister persister;
    private final VideoProgressTracker progressTracker;
    private final GeminiClient geminiClient;

    public VideoJobConsumer(ProcessingJobService jobService,
                             VideoJobProducer jobProducer,
                             YtDlpClient ytDlpClient,
                             VideoIngestionPersister persister,
                             VideoProgressTracker progressTracker,
                             GeminiClient geminiClient) {
        this.jobService = jobService;
        this.jobProducer = jobProducer;
        this.ytDlpClient = ytDlpClient;
        this.persister = persister;
        this.progressTracker = progressTracker;
        this.geminiClient = geminiClient;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_VIDEO_JOBS)
    public void handleJob(String payload) {
        Long jobId;
        try {
            jobId = Long.parseLong(payload.trim());
        } catch (NumberFormatException e) {
            log.error("[JOB] Ignoring invalid job payload: {}", payload);
            return;
        }

        Optional<ProcessingJobService.JobExecutionSnapshot> claimed = jobService.claimForProcessing(jobId);
        if (claimed.isEmpty()) {
            log.info("[JOB] Job {} not found or already terminal - skipping", jobId);
            return;
        }

        ProcessingJobService.JobExecutionSnapshot job = claimed.get();
        log.info("[JOB] Processing job {} (type={}, videoId={}, target={}, attempt={}/{})",
                job.jobId(), job.jobType(), job.videoId(), job.targetLanguage(),
                job.retryCount() + 1, job.maxRetries());

        try {
            if (job.jobType() == JobType.INGEST_VIDEO) {
                executeIngest(job);
            } else {
                executeTranslate(job);
            }
            jobService.markCompleted(job.jobId());
            log.info("[JOB] Job {} COMPLETED (type={}, videoId={})", job.jobId(), job.jobType(), job.videoId());
        } catch (YtDlpProcessException e) {
            boolean retryable = !NON_RETRYABLE_REASONS.contains(e.getFailureReason());
            handleFailure(job, e.getFailureReason(), e.getMessage(), retryable);
        } catch (GeminiUnavailableException e) {
            handleFailure(job, FailureReason.TRANSLATION_FAILED, e.getMessage(), true);
        } catch (TokenizationException e) {
            handleFailure(job, FailureReason.TOKENIZATION_FAILED, e.getMessage(), true);
        } catch (Exception e) {
            log.error("[JOB] Unexpected error on job {}", job.jobId(), e);
            handleFailure(job, FailureReason.UNKNOWN_ERROR, e.getMessage(), true);
        }
    }

    private void executeIngest(ProcessingJobService.JobExecutionSnapshot job) {
        persister.markProcessing(job.videoId());
        YtDlpResult result = ytDlpClient.fetchCaptionsAndMetadata(job.videoId(), job.youtubeVideoId(), job.sourceLanguage());
        List<VttCue> cues = VttParser.parse(result.vttContent());
        progressTracker.beginSaving(job.videoId(), result.title(), result.durationSeconds(), result.sourceLanguage(), cues.size());
        jobService.updateProgress(job.jobId(), 65);

        String targetLanguage = job.targetLanguage();
        boolean needsTranslation = targetLanguage != null && !targetLanguage.isBlank()
                && !targetLanguage.equalsIgnoreCase(result.sourceLanguage());

        List<String> translatedTexts = null;
        if (needsTranslation && !cues.isEmpty()) {
            List<String> sourceTexts = cues.stream().map(VttCue::text).toList();
            translatedTexts = translateInBatches(job.jobId(), sourceTexts, result.sourceLanguage(), targetLanguage, 65, 90);
        }

        persister.persistLines(job.videoId(), result.sourceLanguage(), cues, needsTranslation ? targetLanguage : null, translatedTexts);
        persister.markReady(job.videoId(), job.requestedByUserId());
    }

    private void executeTranslate(ProcessingJobService.JobExecutionSnapshot job) {
        List<String> sourceTexts = persister.loadSourceTexts(job.videoId());
        if (sourceTexts.isEmpty()) {
            return;
        }
        String sourceLanguage = job.sourceLanguage();
        String targetLanguage = job.targetLanguage();
        if (targetLanguage == null || targetLanguage.isBlank() || targetLanguage.equalsIgnoreCase(sourceLanguage)) {
            return;
        }
        jobService.updateProgress(job.jobId(), 10);
        List<String> translatedTexts = translateInBatches(job.jobId(), sourceTexts, sourceLanguage, targetLanguage, 10, 95);
        // Only overwrite old translations once Gemini has translated 100% of the lines.
        persister.replaceTranslations(job.videoId(), targetLanguage, translatedTexts);
    }

    private List<String> translateInBatches(Long jobId, List<String> sourceTexts,
                                             String sourceLanguage, String targetLanguage,
                                             int startPercent, int endPercent) {
        List<String> allTranslations = new ArrayList<>(sourceTexts.size());
        int total = sourceTexts.size();
        for (int start = 0; start < total; start += TRANSLATION_BATCH_SIZE) {
            int end = Math.min(start + TRANSLATION_BATCH_SIZE, total);
            List<String> batch = sourceTexts.subList(start, end);
            allTranslations.addAll(geminiClient.translateLines(batch, sourceLanguage, targetLanguage));
            int progress = startPercent + (int) Math.round((end / (double) total) * (endPercent - startPercent));
            jobService.updateProgress(jobId, progress);
        }
        return allTranslations;
    }

    private void handleFailure(ProcessingJobService.JobExecutionSnapshot job, FailureReason reason,
                                String message, boolean retryable) {
        int nextAttemptCount = job.retryCount() + 1;
        if (retryable && nextAttemptCount < job.maxRetries()) {
            log.warn("[JOB] Job {} failed (attempt {}/{}), scheduling retry. Reason={}: {}",
                    job.jobId(), nextAttemptCount, job.maxRetries(), reason, message);
            jobService.recordRetry(job.jobId(), reason, message);
            jobProducer.publishRetry(job.jobId());
            return;
        }

        log.error("[JOB] Job {} permanently FAILED after {}/{} attempts. Reason={}: {}",
                job.jobId(), nextAttemptCount, job.maxRetries(), reason, message);
        jobService.markFailed(job.jobId(), reason, message);
        if (job.jobType() == JobType.INGEST_VIDEO) {
            persister.markFailed(job.videoId(), reason, message);
        }
        jobProducer.publishDlq(job.jobId());
    }
}
