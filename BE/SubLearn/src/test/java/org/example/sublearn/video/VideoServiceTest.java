package org.example.sublearn.video;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.domain.VideoWatchHistory;
import org.example.sublearn.ingestion.VideoProgressTracker;
import org.example.sublearn.ingestion.YouTubeUrlParser;
import org.example.sublearn.job.ProcessingJobResponse;
import org.example.sublearn.job.ProcessingJobService;
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
    private VideoLibraryService videoLibraryService;
    private ProcessingJobService processingJobService;
    private VideoService service;
    private AppUser user;
    private Video video;

    @BeforeEach
    void setUp() {
        videoRepository = mock(VideoRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        videoWatchHistoryRepository = mock(VideoWatchHistoryRepository.class);
        videoLibraryService = mock(VideoLibraryService.class);
        YouTubeUrlParser youTubeUrlParser = mock(YouTubeUrlParser.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        processingJobService = mock(ProcessingJobService.class);
        service = new VideoService(videoRepository, appUserRepository, youTubeUrlParser,
                progressTracker, videoWatchHistoryRepository, videoLibraryService, processingJobService);

        user = new AppUser("learner@example.com", "hash", "Learner");
        when(appUserRepository.getReferenceById(1L)).thenReturn(user);

        video = new Video("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", user);
        ReflectionTestUtils.setField(video, "id", 10L);
        when(videoRepository.findById(10L)).thenReturn(Optional.of(video));
    }

    @Test
    void reprocessEnqueuesRetranslateJobForReadyVideoWithoutChangingVideoStatus() {
        video.setStatus(VideoStatus.READY);
        video.setSourceLanguage("en");
        ProcessingJobResponse expectedJob = new ProcessingJobResponse(
                55L, 10L, JobType.RETRANSLATE_SUBTITLES, JobStatus.PENDING, "en", "vi", 0, 5, 0, null, null);
        when(processingJobService.createAndEnqueue(video, 1L, JobType.RETRANSLATE_SUBTITLES, "en", "vi"))
                .thenReturn(expectedJob);

        ProcessingJobResponse response = service.reprocess(10L, "vi", 1L);

        assertEquals(55L, response.jobId());
        assertEquals(JobType.RETRANSLATE_SUBTITLES, response.jobType());
        assertEquals(VideoStatus.READY, video.getStatus());
        verify(processingJobService).createAndEnqueue(video, 1L, JobType.RETRANSLATE_SUBTITLES, "en", "vi");
    }

    @Test
    void reprocessRejectsVideoThatIsNotReady() {
        video.setStatus(VideoStatus.FAILED);

        ApiException exception = assertThrows(ApiException.class, () -> service.reprocess(10L, "vi", 1L));

        assertEquals("VIDEO_NOT_READY", exception.getCode());
        verify(processingJobService, never()).createAndEnqueue(any(), any(), any(), any(), any());
    }

    @Test
    void requestTranslationEnqueuesTranslateJobForNewTargetLanguage() {
        video.setStatus(VideoStatus.READY);
        video.setSourceLanguage("en");
        ProcessingJobResponse expectedJob = new ProcessingJobResponse(
                66L, 10L, JobType.TRANSLATE_SUBTITLES, JobStatus.PENDING, "en", "ja", 0, 5, 0, null, null);
        when(processingJobService.createAndEnqueue(video, 1L, JobType.TRANSLATE_SUBTITLES, "en", "ja"))
                .thenReturn(expectedJob);

        ProcessingJobResponse response = service.requestTranslation(10L, "ja", 1L);

        assertEquals(66L, response.jobId());
        assertEquals(JobType.TRANSLATE_SUBTITLES, response.jobType());
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

    @Test
    void cachedReadyVideoIsAddedToNewUsersLibraryWithoutEnqueueingIngestionAgain() {
        VideoRepository repo = mock(VideoRepository.class);
        AppUserRepository userRepo = mock(AppUserRepository.class);
        YouTubeUrlParser urlParser = mock(YouTubeUrlParser.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        VideoWatchHistoryRepository watchHistoryRepository = mock(VideoWatchHistoryRepository.class);
        VideoLibraryService libraryService = mock(VideoLibraryService.class);
        ProcessingJobService jobService = mock(ProcessingJobService.class);
        VideoService svc = new VideoService(repo, userRepo, urlParser,
                progressTracker, watchHistoryRepository, libraryService, jobService);

        AppUser originalUser = new AppUser("first@example.com", "hash", "First");
        Video cachedVideo = new Video("5MuIMqhT8DM", "https://youtu.be/5MuIMqhT8DM", originalUser);
        ReflectionTestUtils.setField(cachedVideo, "id", 42L);
        cachedVideo.setStatus(VideoStatus.READY);

        when(urlParser.extractVideoId("https://youtu.be/5MuIMqhT8DM"))
                .thenReturn(Optional.of("5MuIMqhT8DM"));
        when(repo.findByYoutubeVideoId("5MuIMqhT8DM"))
                .thenReturn(Optional.of(cachedVideo));

        VideoSubmitResponse response = svc.submitVideo(
                "https://youtu.be/5MuIMqhT8DM", "en", "vi", 99L);

        assertEquals(42L, response.videoId());
        assertEquals(VideoStatus.READY, response.status());
        verify(libraryService).addToLibrary(99L, cachedVideo);
        verify(jobService, never()).createAndEnqueue(any(), any(), any(), any(), any());
    }
}
