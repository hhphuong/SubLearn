package org.example.sublearn.video;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.UserVideo;
import org.example.sublearn.domain.Video;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.UserVideoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoLibraryServiceTest {

    @Test
    void updateProgressIsScopedToUserAndClampedToVideoDuration() {
        UserVideoRepository userVideoRepository = mock(UserVideoRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        VideoLibraryService service = new VideoLibraryService(userVideoRepository, appUserRepository);

        AppUser user = new AppUser("learner@example.com", "hash", "Learner");
        Video video = new Video("5MuIMqhT8DM", "https://youtu.be/5MuIMqhT8DM", user);
        video.setDurationSeconds(120);
        UserVideo userVideo = new UserVideo(user, video);

        when(userVideoRepository.findByUser_IdAndVideo_Id(7L, 42L))
                .thenReturn(Optional.of(userVideo));

        service.updateProgress(7L, 42L, 180_000);

        assertEquals(120_000, userVideo.getLastPositionMs());
    }
}
