package org.example.sublearn.video;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.ingestion.VideoIngestionService;
import org.example.sublearn.ingestion.VideoProgressTracker;
import org.example.sublearn.ingestion.YouTubeUrlParser;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.video.dto.VideoSubmitResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoServiceTest {

    @Test
    void cachedReadyVideoIsAddedToNewUsersLibraryWithoutRunningIngestionAgain() {
        VideoRepository videoRepository = mock(VideoRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        YouTubeUrlParser urlParser = mock(YouTubeUrlParser.class);
        VideoIngestionService ingestionService = mock(VideoIngestionService.class);
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        VideoLibraryService libraryService = mock(VideoLibraryService.class);
        VideoService service = new VideoService(videoRepository, appUserRepository, urlParser,
                ingestionService, progressTracker, libraryService);

        AppUser originalUser = new AppUser("first@example.com", "hash", "First");
        Video cachedVideo = new Video("5MuIMqhT8DM", "https://youtu.be/5MuIMqhT8DM", originalUser);
        ReflectionTestUtils.setField(cachedVideo, "id", 42L);
        cachedVideo.setStatus(VideoStatus.READY);

        when(urlParser.extractVideoId("https://youtu.be/5MuIMqhT8DM"))
                .thenReturn(Optional.of("5MuIMqhT8DM"));
        when(videoRepository.findByYoutubeVideoId("5MuIMqhT8DM"))
                .thenReturn(Optional.of(cachedVideo));

        VideoSubmitResponse response = service.submitVideo(
                "https://youtu.be/5MuIMqhT8DM", "en", 99L);

        assertEquals(42L, response.videoId());
        assertEquals(VideoStatus.READY, response.status());
        verify(libraryService).addToLibrary(99L, cachedVideo);
        verify(ingestionService, never()).ingest(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
