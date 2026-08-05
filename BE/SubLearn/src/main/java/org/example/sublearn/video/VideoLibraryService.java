package org.example.sublearn.video;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.UserVideo;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.UserVideoRepository;
import org.example.sublearn.video.dto.VideoLibraryItemResponse;
import org.example.sublearn.video.dto.VideoLibraryPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VideoLibraryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserVideoRepository userVideoRepository;
    private final AppUserRepository appUserRepository;

    public VideoLibraryService(UserVideoRepository userVideoRepository, AppUserRepository appUserRepository) {
        this.userVideoRepository = userVideoRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public void addToLibrary(Long userId, Video video) {
        UserVideo existing = userVideoRepository.findByUser_IdAndVideo_Id(userId, video.getId()).orElse(null);
        if (existing != null) {
            existing.touch();
            return;
        }
        AppUser user = appUserRepository.getReferenceById(userId);
        userVideoRepository.save(new UserVideo(user, video));
    }

    @Transactional(readOnly = true)
    public VideoLibraryPageResponse list(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "lastWatchedAt"));
        Page<UserVideo> result = userVideoRepository
                .findByUser_IdAndVideo_Status(userId, VideoStatus.READY, pageRequest);
        List<VideoLibraryItemResponse> videos = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new VideoLibraryPageResponse(safePage, safeSize, result.getTotalElements(), videos);
    }

    @Transactional
    public void updateProgress(Long userId, Long videoId, long positionMs) {
        UserVideo userVideo = userVideoRepository.findByUser_IdAndVideo_Id(userId, videoId)
                .orElseThrow(() -> ApiException.notFound(
                        "VIDEO_NOT_IN_LIBRARY", "Video is not in this user's library: " + videoId));
        Video video = userVideo.getVideo();
        long maximumPositionMs = video.getDurationSeconds() == null
                ? positionMs
                : Math.max(0, video.getDurationSeconds() * 1000L);
        userVideo.updateProgress(Math.min(positionMs, maximumPositionMs));
    }

    @Transactional(readOnly = true)
    public long getLastPosition(Long userId, Long videoId) {
        return userVideoRepository.findByUser_IdAndVideo_Id(userId, videoId)
                .map(UserVideo::getLastPositionMs)
                .orElse(0L);
    }

    private VideoLibraryItemResponse toResponse(UserVideo userVideo) {
        Video video = userVideo.getVideo();
        return new VideoLibraryItemResponse(video.getId(), video.getYoutubeVideoId(), video.getTitle(),
                video.getSourceLanguage(), video.getDurationSeconds(), userVideo.getLastPositionMs(),
                userVideo.getAddedAt(), userVideo.getLastWatchedAt());
    }
}
