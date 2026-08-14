package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A video's {@code ingest()} call runs on an in-memory {@code @Async} executor (see
 * {@link VideoIngestionService}) with no durable job queue behind it. If the backend process
 * stops or restarts while a video is still PENDING/PROCESSING - e.g. during a redeploy - that
 * async task is simply gone; nothing ever calls {@code markFailed}, so the row is stuck in an
 * "active" status forever. {@link org.example.sublearn.video.VideoService#submitVideo} treats
 * PENDING/PROCESSING as still in flight and just returns the stuck status on resubmission
 * instead of retrying, so without this recovery the same video could never be ingested again.
 *
 * <p>Runs once at startup and marks any such leftover rows FAILED, which makes them retriable
 * (resubmitting the same URL resets a FAILED video back to PENDING and re-ingests it).
 */
@Component
public class StaleIngestionRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleIngestionRecovery.class);

    private final VideoRepository videoRepository;
    private final VideoIngestionPersister persister;

    public StaleIngestionRecovery(VideoRepository videoRepository, VideoIngestionPersister persister) {
        this.videoRepository = videoRepository;
        this.persister = persister;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Video> stale = videoRepository.findByStatusIn(List.of(VideoStatus.PENDING, VideoStatus.PROCESSING));
        for (Video video : stale) {
            log.warn("Marking video {} ({}) FAILED - it was left in {} by a previous server shutdown/restart",
                    video.getId(), video.getYoutubeVideoId(), video.getStatus());
            persister.markFailed(video.getId(), FailureReason.UNKNOWN_ERROR,
                    "Ingestion was interrupted by a server restart. Please try again.");
        }
    }
}
