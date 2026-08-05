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
import org.example.sublearn.video.dto.VideoHistoryEntryResponse;
import org.example.sublearn.video.dto.VideoHistoryPageResponse;
import org.example.sublearn.video.dto.VideoStatusResponse;
import org.example.sublearn.video.dto.VideoSubmitResponse;
import org.example.sublearn.video.dto.VideoSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final VideoIngestionService videoIngestionService;
    private final VideoProgressTracker progressTracker;
    private final VideoWatchHistoryRepository videoWatchHistoryRepository;

    public VideoService(VideoRepository videoRepository, AppUserRepository appUserRepository,
                         YouTubeUrlParser youTubeUrlParser, VideoIngestionService videoIngestionService,
                         VideoProgressTracker progressTracker, VideoWatchHistoryRepository videoWatchHistoryRepository) {
        this.videoRepository = videoRepository;
        this.appUserRepository = appUserRepository;
        this.youTubeUrlParser = youTubeUrlParser;
        this.videoIngestionService = videoIngestionService;
        this.progressTracker = progressTracker;
        this.videoWatchHistoryRepository = videoWatchHistoryRepository;
    }

    @Transactional
    public VideoSubmitResponse submitVideo(String youtubeUrl, String sourceLanguage, Long userId) {
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
                    videoIngestionService.ingest(videoId, youtubeVideoId, sourceLanguage);
                }
            });
        } else {
            videoIngestionService.ingest(videoId, youtubeVideoId, sourceLanguage);
        }
        return new VideoSubmitResponse(video.getId(), video.getStatus());
    }

    @Transactional(readOnly = true)
    public VideoStatusResponse getStatus(Long videoId) {
        Video video = getVideo(videoId);
        // While actively saving lines, the authoritative processedLines/percent live only in
        // VideoProgressTracker's in-memory map (see its javadoc) - the video row's own
        // columns aren't touched again until the whole line-saving transaction commits.
        Integer processedLines = video.getStatus() == VideoStatus.PROCESSING
                ? progressTracker.liveProcessedLines(videoId).orElse(video.getProcessedLines())
                : video.getProcessedLines();
        int progressPercent = video.getStatus() == VideoStatus.PROCESSING
                ? progressTracker.liveProgressPercent(videoId).orElse(video.getProgressPercent())
                : video.getProgressPercent();
        return new VideoStatusResponse(video.getStatus(), video.getFailureReason(), video.getFailureMessage(),
                video.getTitle(), video.getSourceLanguage(), video.getDurationSeconds(), video.getYoutubeVideoId(),
                video.getTotalLines(), processedLines, progressPercent);
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

    /**
     * Re-runs ingestion for a video that already finished (successfully or not) at least
     * once, picking up any ingestion-logic changes since then (e.g. a subtitle-chunking fix)
     * that a code deploy alone can't retroactively apply - {@link #submitVideo} intentionally
     * short-circuits on an already-{@code READY} video to avoid redundant work, so this is the
     * only path back into ingestion for one. Old subtitle lines are cleared as part of the
     * next {@code markProcessing} call (see its javadoc), not here, so this stays a fast,
     * effectively single-column update.
     */
    @Transactional
    public VideoSubmitResponse reprocess(Long videoId) {
        Video video = getVideo(videoId);
        if (video.getStatus() == VideoStatus.PENDING || video.getStatus() == VideoStatus.PROCESSING) {
            return new VideoSubmitResponse(video.getId(), video.getStatus());
        }
        if (video.getSourceLanguage() == null) {
            throw ApiException.conflict("VIDEO_NEVER_INGESTED",
                    "This video has never finished ingestion, so there is nothing to reprocess - submit it instead");
        }
        String sourceLanguage = video.getSourceLanguage();
        String youtubeVideoId = video.getYoutubeVideoId();
        video.setStatus(VideoStatus.PENDING);

        Long id = video.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    videoIngestionService.ingest(id, youtubeVideoId, sourceLanguage);
                }
            });
        } else {
            videoIngestionService.ingest(id, youtubeVideoId, sourceLanguage);
        }
        return new VideoSubmitResponse(video.getId(), video.getStatus());
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
