package org.example.sublearn.video;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AppUser;
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
import org.example.sublearn.video.dto.VideoHistoryEntryResponse;
import org.example.sublearn.video.dto.VideoHistoryPageResponse;
import org.example.sublearn.video.dto.VideoStatusResponse;
import org.example.sublearn.video.dto.VideoSubmitResponse;
import org.example.sublearn.video.dto.VideoSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class VideoService {

    private static final Set<VideoStatus> ACTIVE_STATUSES = EnumSet.of(
            VideoStatus.PENDING, VideoStatus.PROCESSING, VideoStatus.READY);

    private final VideoRepository videoRepository;
    private final AppUserRepository appUserRepository;
    private final YouTubeUrlParser youTubeUrlParser;
    private final VideoProgressTracker progressTracker;
    private final VideoWatchHistoryRepository videoWatchHistoryRepository;
    private final VideoLibraryService videoLibraryService;
    private final ProcessingJobService processingJobService;

    public VideoService(VideoRepository videoRepository, AppUserRepository appUserRepository,
                         YouTubeUrlParser youTubeUrlParser, VideoProgressTracker progressTracker,
                         VideoWatchHistoryRepository videoWatchHistoryRepository,
                         VideoLibraryService videoLibraryService,
                         ProcessingJobService processingJobService) {
        this.videoRepository = videoRepository;
        this.appUserRepository = appUserRepository;
        this.youTubeUrlParser = youTubeUrlParser;
        this.progressTracker = progressTracker;
        this.videoWatchHistoryRepository = videoWatchHistoryRepository;
        this.videoLibraryService = videoLibraryService;
        this.processingJobService = processingJobService;
    }

    @Transactional
    public VideoSubmitResponse submitVideo(String youtubeUrl, String sourceLanguage, Long userId) {
        return submitVideo(youtubeUrl, sourceLanguage, null, userId);
    }

    @Transactional
    public VideoSubmitResponse submitVideo(String youtubeUrl, String sourceLanguage, String targetLanguage, Long userId) {
        String youtubeVideoId = youTubeUrlParser.extractVideoId(youtubeUrl)
                .orElseThrow(() -> ApiException.badRequest("INVALID_URL", "This does not look like a valid YouTube video URL"));

        Video video = videoRepository.findByYoutubeVideoId(youtubeVideoId).orElse(null);
        if (video != null && ACTIVE_STATUSES.contains(video.getStatus())) {
            if (video.getStatus() == VideoStatus.READY) {
                videoLibraryService.addToLibrary(userId, video);
                return new VideoSubmitResponse(video.getId(), video.getStatus(), null);
            }
            Long existingJobId = processingJobService.findLatestIngestJob(video.getId())
                    .map(ProcessingJobResponse::jobId)
                    .orElse(null);
            return new VideoSubmitResponse(video.getId(), video.getStatus(), existingJobId);
        }

        if (video == null) {
            AppUser requestedBy = appUserRepository.getReferenceById(userId);
            video = new Video(youtubeVideoId, youtubeUrl, requestedBy);
            videoRepository.save(video);
        } else {
            // previous attempt FAILED - reset and enqueue a fresh ingestion job
            video.setStatus(VideoStatus.PENDING);
        }

        ProcessingJobResponse job = processingJobService.createAndEnqueue(
                video, userId, JobType.INGEST_VIDEO, sourceLanguage, targetLanguage);
        return new VideoSubmitResponse(video.getId(), video.getStatus(), job.jobId());
    }

    @Transactional(readOnly = true)
    public VideoStatusResponse getStatus(Long videoId) {
        Video video = getVideo(videoId);
        Integer processedLines = video.getStatus() == VideoStatus.PROCESSING
                ? progressTracker.liveProcessedLines(videoId).orElse(video.getProcessedLines())
                : video.getProcessedLines();
        int progressPercent = video.getStatus() == VideoStatus.PROCESSING
                ? progressTracker.liveProgressPercent(videoId).orElse(video.getProgressPercent())
                : video.getProgressPercent();
        ProcessingJobResponse latestJob = processingJobService.findLatestIngestJob(videoId).orElse(null);
        return new VideoStatusResponse(
                video.getStatus(),
                video.getFailureReason(),
                video.getFailureMessage(),
                video.getTitle(),
                video.getSourceLanguage(),
                video.getDurationSeconds(),
                video.getYoutubeVideoId(),
                video.getTotalLines(),
                processedLines,
                progressPercent,
                latestJob != null ? latestJob.jobId() : null,
                latestJob != null ? latestJob.retryCount() : null,
                latestJob != null ? latestJob.maxRetries() : null
        );
    }

    @Transactional(readOnly = true)
    public VideoSummaryResponse getSummary(Long videoId, Long userId) {
        Video video = getVideo(videoId);
        return new VideoSummaryResponse(video.getId(), video.getTitle(), video.getSourceLanguage(),
                video.getDurationSeconds(), video.getStatus(), video.getYoutubeVideoId(),
                videoLibraryService.getLastPosition(userId, videoId));
    }

    Video getVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> ApiException.notFound("VIDEO_NOT_FOUND", "Video not found: " + videoId));
    }

    /**
     * Enqueues an asynchronous translation job for a new target language on an already-READY video.
     */
    @Transactional
    public ProcessingJobResponse requestTranslation(Long videoId, String targetLanguage, Long userId) {
        Video video = getVideo(videoId);
        if (video.getStatus() != VideoStatus.READY) {
            throw ApiException.conflict("VIDEO_NOT_READY", "Video is not ready yet (status: " + video.getStatus() + ")");
        }
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw ApiException.badRequest("INVALID_TARGET_LANGUAGE", "Target language is required");
        }
        if (targetLanguage.equalsIgnoreCase(video.getSourceLanguage())) {
            throw ApiException.badRequest("SAME_LANGUAGE", "Target language matches the video's source language");
        }
        return processingJobService.createAndEnqueue(
                video, userId, JobType.TRANSLATE_SUBTITLES, video.getSourceLanguage(), targetLanguage);
    }

    /**
     * Enqueues an asynchronous re-translation job for the selected target language only,
     * reusing the persisted subtitle lines without re-fetching from YouTube.
     */
    @Transactional
    public ProcessingJobResponse reprocess(Long videoId, String targetLanguage, Long userId) {
        Video video = getVideo(videoId);
        if (video.getStatus() != VideoStatus.READY) {
            throw ApiException.conflict("VIDEO_NOT_READY", "Video is not ready yet (status: " + video.getStatus() + ")");
        }
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw ApiException.badRequest("INVALID_TARGET_LANGUAGE", "Target language is required for reprocessing translation");
        }
        if (targetLanguage.equalsIgnoreCase(video.getSourceLanguage())) {
            throw ApiException.badRequest("SAME_LANGUAGE", "Target language matches the video's source language");
        }
        return processingJobService.createAndEnqueue(
                video, userId, JobType.RETRANSLATE_SUBTITLES, video.getSourceLanguage(), targetLanguage);
    }

    @Transactional
    public void recordWatch(Long videoId, Long userId) {
        AppUser user = appUserRepository.getReferenceById(userId);
        Video video = getVideo(videoId);
        VideoWatchHistory history = videoWatchHistoryRepository.findByUserAndVideo(user, video)
                .orElseGet(() -> new VideoWatchHistory(user, video));
        history.touch();
        videoWatchHistoryRepository.save(history);
    }

    /** Newest first, capped at 50 total regardless of how far the caller pages. */
    @Transactional(readOnly = true)
    public VideoHistoryPageResponse getHistory(Long userId, int page, int size) {
        AppUser user = appUserRepository.getReferenceById(userId);
        List<VideoWatchHistory> all = videoWatchHistoryRepository.findTop50ByUserOrderByLastWatchedAtDesc(user);

        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<VideoHistoryEntryResponse> entries = all.subList(from, to).stream()
                .map(h -> new VideoHistoryEntryResponse(h.getVideo().getId(), h.getVideo().getYoutubeVideoId(),
                        h.getVideo().getTitle(), h.getVideo().getSourceLanguage(), h.getVideo().getDurationSeconds(),
                        h.getLastWatchedAt()))
                .toList();
        return new VideoHistoryPageResponse(page, size, total, entries);
    }
}
