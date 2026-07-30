package org.example.sublearn.ingestion;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleToken;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.nlp.TokenSpan;
import org.example.sublearn.nlp.Tokenizer;
import org.example.sublearn.nlp.TokenizerFactory;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.SubtitleTokenRepository;
import org.example.sublearn.repository.VideoRepository;
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

    private final VideoRepository videoRepository;
    private final SubtitleLineRepository subtitleLineRepository;
    private final SubtitleTokenRepository subtitleTokenRepository;
    private final TokenizerFactory tokenizerFactory;

    public VideoIngestionPersister(VideoRepository videoRepository, SubtitleLineRepository subtitleLineRepository,
                                    SubtitleTokenRepository subtitleTokenRepository, TokenizerFactory tokenizerFactory) {
        this.videoRepository = videoRepository;
        this.subtitleLineRepository = subtitleLineRepository;
        this.subtitleTokenRepository = subtitleTokenRepository;
        this.tokenizerFactory = tokenizerFactory;
    }

    @Transactional
    public void markProcessing(Long videoId) {
        getVideo(videoId).setStatus(VideoStatus.PROCESSING);
    }

    @Transactional
    public void persistReady(Long videoId, YtDlpResult result, List<VttCue> cues) {
        Video video = getVideo(videoId);
        video.setTitle(result.title());
        video.setDurationSeconds(result.durationSeconds());
        video.setSourceLanguage(result.sourceLanguage());

        Tokenizer tokenizer = tokenizerFactory.get(result.sourceLanguage());
        int sequence = 0;
        for (VttCue cue : cues) {
            SubtitleLine line = new SubtitleLine(video, sequence++, cue.startMs(), cue.endMs(), cue.text());
            subtitleLineRepository.save(line);

            List<TokenSpan> spans = tokenizer.tokenize(cue.text());
            int tokenSequence = 0;
            for (TokenSpan span : spans) {
                SubtitleToken token = new SubtitleToken(line, tokenSequence++, span.startCharOffset(), span.endCharOffset(),
                        span.surfaceText(), span.surfaceText().toLowerCase(Locale.ROOT).trim(),
                        span.tokenType(), span.partOfSpeech());
                subtitleTokenRepository.save(token);
            }
        }
        video.setStatus(VideoStatus.READY);
    }

    @Transactional
    public void markFailed(Long videoId, FailureReason reason, String message) {
        getVideo(videoId).markFailed(reason, message);
    }

    private Video getVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> ApiException.notFound("VIDEO_NOT_FOUND", "Video not found: " + videoId));
    }
}
