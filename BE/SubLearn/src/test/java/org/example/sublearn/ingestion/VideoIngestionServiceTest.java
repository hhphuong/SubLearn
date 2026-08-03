package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoIngestionServiceTest {

    @Test
    void ingestPersistsSubtitlesWhenYtDlpSucceeds() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker);

        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world
                """;
        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123")).thenReturn(new YtDlpResult("Title", 10, "en", vtt));

        service.ingest(1L, "abc123");

        verify(persister).markProcessing(1L);
        verify(progressTracker).beginSaving(1L, "Title", 10, "en", 1);
        verify(persister).persistLines(eq(1L), eq("en"), argThat(cues -> cues.size() == 1));
        verify(persister).markReady(1L);
        verify(persister, never()).markFailed(anyLong(), any(), any());
    }

    @Test
    void ingestMarksFailedWithoutThrowingWhenNoCaptionsAreAvailable() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker);

        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123"))
                .thenThrow(new YtDlpProcessException(FailureReason.NO_CAPTIONS_AVAILABLE, "no captions found"));

        assertDoesNotThrow(() -> service.ingest(1L, "abc123"));

        verify(persister).markProcessing(1L);
        verify(persister).markFailed(1L, FailureReason.NO_CAPTIONS_AVAILABLE, "no captions found");
    }

    @Test
    void ingestMapsUnexpectedExceptionsToUnknownErrorWithoutThrowing() {
        YtDlpClient ytDlpClient = mock(YtDlpClient.class);
        VideoIngestionPersister persister = mock(VideoIngestionPersister.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        VideoIngestionService service = new VideoIngestionService(ytDlpClient, persister, progressTracker);

        when(ytDlpClient.fetchCaptionsAndMetadata(1L, "abc123")).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> service.ingest(1L, "abc123"));

        verify(persister).markFailed(eq(1L), eq(FailureReason.UNKNOWN_ERROR), any());
    }
}
