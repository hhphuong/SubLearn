package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.gemini.GeminiClient;
import org.example.sublearn.gemini.GeminiUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoIngestionServiceTest {

    @Test
    void ingestPersistsSubtitlesAndTranslationsWhenYtDlpAndGeminiSucceed() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker, geminiClient);

        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world
                """;
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en")).thenReturn(new YtDlpResult("Title", 10, "en", vtt));
        when(geminiClient.translateLines(List.of("Hello world"), "en", "vi")).thenReturn(List.of("Xin chào thế giới"));

        service.ingest(1L, "abc123", "en", "vi", 7L);

        verify(persister).markProcessing(1L);
        verify(progressTracker).beginSaving(1L, "Title", 10, "en", 1);
        verify(geminiClient).translateLines(List.of("Hello world"), "en", "vi");
        verify(persister).persistLines(eq(1L), eq("en"), argThat(cues -> cues.size() == 1),
                eq("vi"), eq(List.of("Xin chào thế giới")));
        verify(persister).markReady(1L, 7L);
        verify(persister, never()).markFailed(anyLong(), any(), any());
    }

    @Test
    void ingestSkipsGeminiWhenSourceAndTargetLanguageMatch() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker, geminiClient);

        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world
                """;
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en")).thenReturn(new YtDlpResult("Title", 10, "en", vtt));

        service.ingest(1L, "abc123", "en", "en", 7L);

        verify(geminiClient, never()).translateLines(anyList(), anyString(), anyString());
        verify(persister).persistLines(eq(1L), eq("en"), argThat(cues -> cues.size() == 1), isNull(), isNull());
        verify(persister).markReady(1L, 7L);
        verify(persister, never()).markFailed(anyLong(), any(), any());
    }

    @Test
    void ingestMarksFailedAndDoesNotPersistSubtitlesWhenGeminiTranslationFails() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker, geminiClient);

        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world
                """;
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en")).thenReturn(new YtDlpResult("Title", 10, "en", vtt));
        when(geminiClient.translateLines(List.of("Hello world"), "en", "vi"))
                .thenThrow(new GeminiUnavailableException("API key expired"));

        assertDoesNotThrow(() -> service.ingest(1L, "abc123", "en", "vi", 7L));

        verify(persister).markProcessing(1L);
        verify(persister, never()).persistLines(anyLong(), anyString(), anyList(), any(), any());
        verify(persister, never()).markReady(anyLong(), any());
        verify(persister).markFailed(1L, FailureReason.TRANSLATION_FAILED, "API key expired");
    }

    @Test
    void ingestMarksFailedWithoutThrowingWhenNoCaptionsAreAvailable() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker, geminiClient);

        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en"))
                .thenThrow(new YtDlpProcessException(FailureReason.NO_CAPTIONS_AVAILABLE, "no captions found"));

        assertDoesNotThrow(() -> service.ingest(1L, "abc123", "en"));

        verify(persister).markProcessing(1L);
        verify(persister).markFailed(1L, FailureReason.NO_CAPTIONS_AVAILABLE, "no captions found");
    }

    @Test
    void ingestMapsUnexpectedExceptionsToUnknownErrorWithoutThrowing() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker, geminiClient);

        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123", "en")).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> service.ingest(1L, "abc123", "en"));

        verify(persister).markFailed(eq(1L), eq(FailureReason.UNKNOWN_ERROR), any());
    }
}
