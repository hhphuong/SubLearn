package org.example.sublearn.video;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.ingestion.VideoIngestionService;
import org.example.sublearn.ingestion.YouTubeUrlParser;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.video.dto.VideoStatusResponse;
import org.example.sublearn.video.dto.VideoSubmitResponse;
import org.example.sublearn.video.dto.VideoSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.Set;

@Service
public class VideoService {

    private static final Set<VideoStatus> ACTIVE_STATUSES = EnumSet.of(
            VideoStatus.PENDING, VideoStatus.PROCESSING, VideoStatus.READY);

    private final VideoRepository videoRepository;
    private final AppUserRepository appUserRepository;
    private final YouTubeUrlParser youTubeUrlParser;
    private final VideoIngestionService videoIngestionService;

    public VideoService(VideoRepository videoRepository, AppUserRepository appUserRepository,
                         YouTubeUrlParser youTubeUrlParser, VideoIngestionService videoIngestionService) {
        this.videoRepository = videoRepository;
        this.appUserRepository = appUserRepository;
        this.youTubeUrlParser = youTubeUrlParser;
        this.videoIngestionService = videoIngestionService;
    }

    @Transactional
    public VideoSubmitResponse submitVideo(String youtubeUrl, Long userId) {
        String youtubeVideoId = youTubeUrlParser.extractVideoId(youtubeUrl)
                .orElseThrow(() -> ApiException.badRequest("INVALID_URL", "This does not look like a valid YouTube video URL"));

        Video video = videoRepository.findByYoutubeVideoId(youtubeVideoId).orElse(null);
        if (video != null && ACTIVE_STATUSES.contains(video.getStatus())) {
            return new VideoSubmitResponse(video.getId(), video.getStatus());
        }

        if (video == null) {
            AppUser requestedBy = appUserRepository.getReferenceById(userId);
            video = new Video(youtubeVideoId, youtubeUrl, requestedBy);
            videoRepository.save(video);
        } else {
            // previous attempt FAILED - reset and retry
            video.setStatus(VideoStatus.PENDING);
        }

        Long videoId = video.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    videoIngestionService.ingest(videoId, youtubeVideoId);
                }
            });
        } else {
            videoIngestionService.ingest(videoId, youtubeVideoId);
        }
        return new VideoSubmitResponse(video.getId(), video.getStatus());
    }

    @Transactional(readOnly = true)
    public VideoStatusResponse getStatus(Long videoId) {
        Video video = getVideo(videoId);
        return new VideoStatusResponse(video.getStatus(), video.getFailureReason(), video.getFailureMessage(),
                video.getTitle(), video.getSourceLanguage(), video.getDurationSeconds(), video.getYoutubeVideoId());
    }

    @Transactional(readOnly = true)
    public VideoSummaryResponse getSummary(Long videoId) {
        Video video = getVideo(videoId);
        return new VideoSummaryResponse(video.getId(), video.getTitle(), video.getSourceLanguage(),
                video.getDurationSeconds(), video.getStatus(), video.getYoutubeVideoId());
    }

    Video getVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> ApiException.notFound("VIDEO_NOT_FOUND", "Video not found: " + videoId));
    }
}
