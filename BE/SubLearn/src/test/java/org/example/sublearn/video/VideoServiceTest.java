package org.example.sublearn.video;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.domain.VideoWatchHistory;
import org.example.sublearn.ingestion.VideoIngestionService;
import org.example.sublearn.ingestion.VideoProgressTracker;
import org.example.sublearn.ingestion.YouTubeUrlParser;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.repository.VideoWatchHistoryRepository;
import org.example.sublearn.video.dto.VideoHistoryPageResponse;
import org.example.sublearn.video.dto.VideoSubmitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoServiceTest {

    private VideoRepository videoRepository;
    private AppUserRepository appUserRepository;
    private VideoWatchHistoryRepository videoWatchHistoryRepository;
    private VideoIngestionService videoIngestionService;
    private VideoService service;
    private AppUser user;
    private Video video;

    @BeforeEach
    void setUp() {
        videoRepository = mock(VideoRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        videoWatchHistoryRepository = mock(VideoWatchHistoryRepository.class);
        YouTubeUrlParser youTubeUrlParser = mock(YouTubeUrlParser.class);
        videoIngestionService = mock(VideoIngestionService.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        service = new VideoService(videoRepository, appUserRepository, youTubeUrlParser, videoIngestionService,
                progressTracker, videoWatchHistoryRepository);

        user = new AppUser("learner@example.com", "hash", "Learner");
        when(appUserRepository.getReferenceById(1L)).thenReturn(user);

        video = new Video("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", user);
        // getId() is normally only populated once JPA assigns it on save(); reprocess() needs
        // a real id to pass through to ingest(), so this stand-in mirrors what a row loaded
        // back out of the repository would already have.
        ReflectionTestUtils.setField(video, "id", 10L);
        when(videoRepository.findById(10L)).thenReturn(Optional.of(video));
    }

    @Test
    void reprocessResetsAnAlreadyReadyVideoToPendingAndRetriggersIngestion() {
        video.setStatus(VideoStatus.READY);
        video.setSourceLanguage("en");

        VideoSubmitResponse response = service.reprocess(10L);

        assertEquals(VideoStatus.PENDING, video.getStatus());
        assertEquals(VideoStatus.PENDING, response.status());
        verify(videoIngestionService).ingest(eq(10L), eq("dQw4w9WgXcQ"), eq("en"));
    }

    @Test
    void reprocessRejectsAVideoThatHasNeverFinishedIngestionOnce() {
        // FAILED with sourceLanguage still null: the one and only ingestion attempt never
        // got far enough to detect a language, so there's nothing a prior run produced to redo.
        video.setStatus(VideoStatus.FAILED);

        ApiException exception = assertThrows(ApiException.class, () -> service.reprocess(10L));

        assertEquals("VIDEO_NEVER_INGESTED", exception.getCode());
        verify(videoIngestionService, never()).ingest(any(), any(), any());
    }

    @Test
    void reprocessIsANoOpWhileAlreadyPendingOrProcessing() {
        video.setStatus(VideoStatus.PROCESSING);
        video.setSourceLanguage("en");

        VideoSubmitResponse response = service.reprocess(10L);

        assertEquals(VideoStatus.PROCESSING, response.status());
        verify(videoIngestionService, never()).ingest(any(), any(), any());
    }

    @Test
    void recordWatchCreatesHistoryRowOnFirstWatchAndTouchesItOnSubsequentWatches() {
        when(videoWatchHistoryRepository.findByUserAndVideo(user, video)).thenReturn(Optional.empty());

        service.recordWatch(10L, 1L);

        ArgumentCaptor<VideoWatchHistory> captor = ArgumentCaptor.forClass(VideoWatchHistory.class);
        verify(videoWatchHistoryRepository).save(captor.capture());
        VideoWatchHistory saved = captor.getValue();
        assertEquals(video, saved.getVideo());

        when(videoWatchHistoryRepository.findByUserAndVideo(user, video)).thenReturn(Optional.of(saved));
        var firstWatchedAt = saved.getLastWatchedAt();

        service.recordWatch(10L, 1L);

        verify(videoWatchHistoryRepository, times(2)).save(any());
        // touch() should have bumped the timestamp forward (or at least not gone backwards).
        assert !saved.getLastWatchedAt().isBefore(firstWatchedAt);
    }

    @Test
    void getHistoryCapsAndPaginatesInMemoryOverTheTop50Rows() {
        List<VideoWatchHistory> history = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Video v = new Video("video" + i, "https://www.youtube.com/watch?v=video" + i, user);
            history.add(new VideoWatchHistory(user, v));
        }
        when(videoWatchHistoryRepository.findTop50ByUserOrderByLastWatchedAtDesc(user)).thenReturn(history);

        VideoHistoryPageResponse firstPage = service.getHistory(1L, 0, 12);
        assertEquals(50, firstPage.totalElements());
        assertEquals(12, firstPage.entries().size());

        VideoHistoryPageResponse lastPage = service.getHistory(1L, 4, 12);
        assertEquals(2, lastPage.entries().size());

        VideoHistoryPageResponse beyondEnd = service.getHistory(1L, 10, 12);
        assertEquals(0, beyondEnd.entries().size());
    }
}
