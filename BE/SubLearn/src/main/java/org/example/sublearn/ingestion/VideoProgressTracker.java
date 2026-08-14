package org.example.sublearn.ingestion;

import org.example.sublearn.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live ingestion progress so polling clients see it update in real time instead of
 * only after the whole ingestion transaction commits.
 *
 * <p>The percentage is driven entirely by real, observed milestones (yt-dlp log lines and
 * actual subtitle lines persisted) - never by a timer or animation.
 *
 * <p>Per-line updates ({@link #updateProcessedLines}) are kept in memory rather than
 * committed to the {@code video} row: InnoDB takes an implicit shared lock on a row
 * referenced by a foreign key for every child-row insert, so while
 * {@code VideoIngestionPersister.persistLines} is inserting {@code subtitle_line} rows (FK
 * to {@code video}), it holds a standing shared lock on that same video row for the whole
 * loop. A separate transaction trying to {@code UPDATE} that row from inside the same loop
 * would need an exclusive lock and block on its own caller's shared lock until the
 * {@code innodb_lock_wait_timeout} - a self-inflicted "Lock wait timeout exceeded" that was
 * observed in practice. Metadata/stage updates outside that loop (no FK inserts in flight)
 * are unaffected and still write straight to the database.
 */
@Service
public class VideoProgressTracker {

    /** Percentage reached once yt-dlp has finished and cue parsing is done; the saving loop fills the rest. */
    static final int SAVING_STAGE_START_PERCENT = 65;

    private record LiveProgress(int processedLines, int totalLines) {
    }

    private final VideoRepository videoRepository;
    private final Map<Long, LiveProgress> liveProgress = new ConcurrentHashMap<>();

    public VideoProgressTracker(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFetchStage(Long videoId, int percent) {
        videoRepository.findById(videoId).ifPresent(video -> video.setProgressPercent(percent));
    }

    /**
     * Records video metadata and the subtitle line count in its own transaction, right
     * before the (separate) line-saving transaction starts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginSaving(Long videoId, String title, Integer durationSeconds, String sourceLanguage, int totalLines) {
        videoRepository.findById(videoId).ifPresent(video -> {
            video.setTitle(title);
            video.setDurationSeconds(durationSeconds);
            video.setSourceLanguage(sourceLanguage);
            video.setTotalLines(totalLines);
            video.setProgressPercent(SAVING_STAGE_START_PERCENT);
        });
        liveProgress.put(videoId, new LiveProgress(0, totalLines));
    }

    /** In-memory only - see class javadoc for why this can't safely write to the video row. */
    public void updateProcessedLines(Long videoId, int processedLines, int totalLines) {
        liveProgress.put(videoId, new LiveProgress(processedLines, totalLines));
    }

    public Optional<Integer> liveProcessedLines(Long videoId) {
        return Optional.ofNullable(liveProgress.get(videoId)).map(LiveProgress::processedLines);
    }

    public Optional<Integer> liveProgressPercent(Long videoId) {
        return Optional.ofNullable(liveProgress.get(videoId)).map(p -> {
            int span = 100 - SAVING_STAGE_START_PERCENT;
            int percent = p.totalLines() <= 0
                    ? SAVING_STAGE_START_PERCENT
                    : SAVING_STAGE_START_PERCENT + (int) Math.round(p.processedLines() * (double) span / p.totalLines());
            return Math.min(99, percent);
        });
    }

    /** Called once ingestion finishes (success or failure) so the map doesn't grow unbounded. */
    public void clear(Long videoId) {
        liveProgress.remove(videoId);
    }
}
