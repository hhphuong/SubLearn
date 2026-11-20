package org.example.sublearn.ingestion;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleLineTranslation;
import org.example.sublearn.domain.SubtitleToken;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.nlp.TokenSpan;
import org.example.sublearn.nlp.Tokenizer;
import org.example.sublearn.nlp.TokenizerFactory;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.SubtitleLineTranslationRepository;
import org.example.sublearn.repository.SubtitleTokenRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.video.VideoLibraryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Holds the transactional steps of video ingestion as a separate bean from
 * {@link VideoIngestionService} so that {@code @Transactional} is actually applied by
 * Spring's proxy: calling these methods from within the same bean as the {@code @Async}
 * entry point would be a same-instance ("self-invocation") call that bypasses the proxy.
 */
@Service
public class VideoIngestionPersister {

    private static final int PROGRESS_UPDATE_INTERVAL = 5;

    private final VideoRepository videoRepository;
    private final SubtitleLineRepository subtitleLineRepository;
    private final SubtitleTokenRepository subtitleTokenRepository;
    private final SubtitleLineTranslationRepository subtitleLineTranslationRepository;
    private final TokenizerFactory tokenizerFactory;
    private final VideoProgressTracker progressTracker;
    private final VideoLibraryService videoLibraryService;

    public VideoIngestionPersister(VideoRepository videoRepository, SubtitleLineRepository subtitleLineRepository,
                                    SubtitleTokenRepository subtitleTokenRepository,
                                    SubtitleLineTranslationRepository subtitleLineTranslationRepository,
                                    TokenizerFactory tokenizerFactory,
                                    VideoProgressTracker progressTracker,
                                    VideoLibraryService videoLibraryService) {
        this.videoRepository = videoRepository;
        this.subtitleLineRepository = subtitleLineRepository;
        this.subtitleTokenRepository = subtitleTokenRepository;
        this.subtitleLineTranslationRepository = subtitleLineTranslationRepository;
        this.tokenizerFactory = tokenizerFactory;
        this.progressTracker = progressTracker;
        this.videoLibraryService = videoLibraryService;
    }

    /**
     * Clears any subtitle lines from a previous attempt before (re-)starting ingestion.
     * Without this, a reprocess of an already-READY video - or a retry after a prior attempt
     * failed partway through {@link #persistLines}, leaving some lines already saved - would
     * append a second, overlapping set of lines with sequence numbers starting back at 0
     * instead of replacing them.
     */
    @Transactional
    public void markProcessing(Long videoId) {
        Video video = getVideo(videoId);
        subtitleLineRepository.deleteByVideo(video);
        video.setStatus(VideoStatus.PROCESSING);
        video.resetProgress();
        progressTracker.clear(videoId);
    }

    @Transactional
    public void persistLines(Long videoId, String sourceLanguage, List<VttCue> cues) {
        persistLines(videoId, sourceLanguage, cues, null, null);
    }

    /**
     * Persists subtitle lines, tokens, and optional pre-translated lines in one transaction.
     * {@code subtitle_line.video_id} is a foreign key, so every insert here takes an implicit
     * InnoDB shared lock on the referenced {@code video} row for the lifetime of this transaction
     * - see {@link VideoProgressTracker}'s javadoc for why per-line progress is tracked in memory
     * instead of written to that row from inside this loop.
     */
    @Transactional
    public void persistLines(Long videoId, String sourceLanguage, List<VttCue> cues,
                             String targetLanguage, List<String> translatedTexts) {
        Video video = getVideo(videoId);
        Tokenizer tokenizer = tokenizerFactory.get(sourceLanguage);
        boolean saveTranslations = targetLanguage != null && !targetLanguage.isBlank()
                && translatedTexts != null && translatedTexts.size() == cues.size();
        int sequence = 0;
        int processed = 0;
        for (int i = 0; i < cues.size(); i++) {
            VttCue cue = cues.get(i);
            SubtitleLine line = new SubtitleLine(video, sequence++, cue.startMs(), cue.endMs(), cue.text());
            subtitleLineRepository.save(line);

            if (saveTranslations) {
                subtitleLineTranslationRepository.save(new SubtitleLineTranslation(line, targetLanguage, translatedTexts.get(i)));
            }

            List<TokenSpan> spans = tokenizer.tokenize(cue.text());
            int tokenSequence = 0;
            for (TokenSpan span : spans) {
                SubtitleToken token = new SubtitleToken(line, tokenSequence++, span.startCharOffset(), span.endCharOffset(),
                        span.surfaceText(), span.surfaceText().toLowerCase(Locale.ROOT).trim(),
                        span.tokenType(), span.partOfSpeech());
                subtitleTokenRepository.save(token);
            }

            processed++;
            if (processed % PROGRESS_UPDATE_INTERVAL == 0 || processed == cues.size()) {
                progressTracker.updateProcessedLines(videoId, processed, cues.size());
            }
        }
    }

    @Transactional
    public void markReady(Long videoId) {
        markReady(videoId, null);
    }

    @Transactional
    public void markReady(Long videoId, Long requestedByUserId) {
        Video video = getVideo(videoId);
        video.markReady();
        if (requestedByUserId != null) {
            videoLibraryService.addToLibrary(requestedByUserId, video);
        }
        progressTracker.clear(videoId);
    }

    @Transactional
    public void markFailed(Long videoId, FailureReason reason, String message) {
        Video video = getVideo(videoId);
        subtitleLineRepository.deleteByVideo(video);
        video.markFailed(reason, message);
        progressTracker.clear(videoId);
    }

    private Video getVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> ApiException.notFound("VIDEO_NOT_FOUND", "Video not found: " + videoId));
    }
}
