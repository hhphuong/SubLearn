package org.example.sublearn.job;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.gemini.GeminiClient;
import org.example.sublearn.gemini.GeminiUnavailableException;
import org.example.sublearn.ingestion.VideoIngestionPersister;
import org.example.sublearn.ingestion.VideoProgressTracker;
import org.example.sublearn.ingestion.YtDlpClient;
import org.example.sublearn.ingestion.YtDlpProcessException;
import org.example.sublearn.ingestion.YtDlpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoJobConsumerTest {

    private ProcessingJobService jobService;
    private VideoJobProducer jobProducer;
    private YtDlpClient ytDlpClient;
    private VideoIngestionPersister persister;
    private VideoProgressTracker progressTracker;
    private GeminiClient geminiClient;
    private VideoJobConsumer consumer;

    @BeforeEach
    void setUp() {
        jobService = mock(ProcessingJobService.class);
        jobProducer = mock(VideoJobProducer.class);
        ytDlpClient = mock(YtDlpClient.class);
        persister = mock(VideoIngestionPersister.class);
        progressTracker = mock(VideoProgressTracker.class);
        geminiClient = mock(GeminiClient.class);
        consumer = new VideoJobConsumer(jobService, jobProducer, ytDlpClient, persister, progressTracker, geminiClient);
    }

    @Test
    void ingestJobSucceedsAndMarksVideoReadyAndJobCompleted() {
        var snapshot = new ProcessingJobService.JobExecutionSnapshot(
                100L, 1L, "abc123", JobType.INGEST_VIDEO, "en", "vi", 7L, 0, 5);
        when(jobService.claimForProcessing(100L)).thenReturn(Optional.of(snapshot));
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world
                """;
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en"))
                .thenReturn(new YtDlpResult("Title", 10, "en", vtt));
        when(geminiClient.translateLines(List.of("Hello world"), "en", "vi"))
                .thenReturn(List.of("Xin chào thế giới"));

        consumer.handleJob("100");

        verify(persister).markProcessing(1L);
        verify(persister).persistLines(eq(1L), eq("en"), argThat(cues -> cues.size() == 1),
                eq("vi"), eq(List.of("Xin chào thế giới")));
        verify(persister).markReady(1L, 7L);
        verify(jobService).markCompleted(100L);
        verify(jobProducer, never()).publishRetry(anyLong());
        verify(jobProducer, never()).publishDlq(anyLong());
    }

    @Test
    void geminiFailureSchedulesRetryWhenBelowMaxRetries() {
        var snapshot = new ProcessingJobService.JobExecutionSnapshot(
                100L, 1L, "abc123", JobType.INGEST_VIDEO, "en", "vi", 7L, 2, 5);
        when(jobService.claimForProcessing(100L)).thenReturn(Optional.of(snapshot));
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world
                """;
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en"))
                .thenReturn(new YtDlpResult("Title", 10, "en", vtt));
        when(geminiClient.translateLines(List.of("Hello world"), "en", "vi"))
                .thenThrow(new GeminiUnavailableException("503 overloaded"));

        consumer.handleJob("100");

        verify(jobService).recordRetry(100L, FailureReason.TRANSLATION_FAILED, "503 overloaded");
        verify(jobProducer).publishRetry(100L);
        verify(jobService, never()).markFailed(anyLong(), any(), any());
        verify(persister, never()).markFailed(anyLong(), any(), any());
    }

    @Test
    void geminiFailureMarksFailedAndSendsToDlqOnFinalAttempt() {
        var snapshot = new ProcessingJobService.JobExecutionSnapshot(
                100L, 1L, "abc123", JobType.INGEST_VIDEO, "en", "vi", 7L, 4, 5);
        when(jobService.claimForProcessing(100L)).thenReturn(Optional.of(snapshot));
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world
                """;
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en"))
                .thenReturn(new YtDlpResult("Title", 10, "en", vtt));
        when(geminiClient.translateLines(List.of("Hello world"), "en", "vi"))
                .thenThrow(new GeminiUnavailableException("API key expired"));

        consumer.handleJob("100");

        verify(jobService).markFailed(100L, FailureReason.TRANSLATION_FAILED, "API key expired");
        verify(persister).markFailed(1L, FailureReason.TRANSLATION_FAILED, "API key expired");
        verify(jobProducer).publishDlq(100L);
        verify(jobProducer, never()).publishRetry(anyLong());
    }

    @Test
    void nonRetryableYtDlpErrorFailsImmediatelyWithoutRetrying() {
        var snapshot = new ProcessingJobService.JobExecutionSnapshot(
                100L, 1L, "abc123", JobType.INGEST_VIDEO, "en", "vi", 7L, 0, 5);
        when(jobService.claimForProcessing(100L)).thenReturn(Optional.of(snapshot));
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en"))
                .thenThrow(new YtDlpProcessException(FailureReason.NO_CAPTIONS_AVAILABLE, "no captions"));

        consumer.handleJob("100");

        verify(jobService).markFailed(100L, FailureReason.NO_CAPTIONS_AVAILABLE, "no captions");
        verify(persister).markFailed(1L, FailureReason.NO_CAPTIONS_AVAILABLE, "no captions");
        verify(jobProducer).publishDlq(100L);
        verify(jobProducer, never()).publishRetry(anyLong());
    }

    @Test
    void retranslateJobReusesPersistedSubtitlesWithoutCallingYtDlp() {
        var snapshot = new ProcessingJobService.JobExecutionSnapshot(
                200L, 1L, "abc123", JobType.RETRANSLATE_SUBTITLES, "en", "ja", 7L, 0, 5);
        when(jobService.claimForProcessing(200L)).thenReturn(Optional.of(snapshot));
        when(persister.loadSourceTexts(1L)).thenReturn(List.of("Hello", "World"));
        when(geminiClient.translateLines(List.of("Hello", "World"), "en", "ja"))
                .thenReturn(List.of("こんにちは", "世界"));

        consumer.handleJob("200");

        verify(ytDlpClient, never()).fetchCaptionsAndMetadata(anyLong(), anyString(), anyString());
        verify(persister).replaceTranslations(1L, "ja", List.of("こんにちは", "世界"));
        verify(jobService).markCompleted(200L);
    }

    @Test
    void failedRetranslateJobDoesNotDeleteOldTranslations() {
        var snapshot = new ProcessingJobService.JobExecutionSnapshot(
                200L, 1L, "abc123", JobType.RETRANSLATE_SUBTITLES, "en", "ja", 7L, 4, 5);
        when(jobService.claimForProcessing(200L)).thenReturn(Optional.of(snapshot));
        when(persister.loadSourceTexts(1L)).thenReturn(List.of("Hello"));
        when(geminiClient.translateLines(List.of("Hello"), "en", "ja"))
                .thenThrow(new GeminiUnavailableException("quota exceeded"));

        consumer.handleJob("200");

        verify(persister, never()).replaceTranslations(anyLong(), anyString(), anyList());
        verify(persister, never()).markFailed(anyLong(), any(), any());
        verify(jobService).markFailed(200L, FailureReason.TRANSLATION_FAILED, "quota exceeded");
        verify(jobProducer).publishDlq(200L);
    }
}
