package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleToken;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.nlp.KuromojiTokenizer;
import org.example.sublearn.nlp.OpenNlpTokenizer;
import org.example.sublearn.nlp.TokenizerFactory;
import org.example.sublearn.nlp.WhitespaceFallbackTokenizer;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.SubtitleTokenRepository;
import org.example.sublearn.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoIngestionPersisterTest {

    private VideoRepository videoRepository;
    private SubtitleLineRepository subtitleLineRepository;
    private SubtitleTokenRepository subtitleTokenRepository;
    private VideoIngestionPersister persister;
    private Video video;

    @BeforeEach
    void setUp() {
        videoRepository = mock(VideoRepository.class);
        subtitleLineRepository = mock(SubtitleLineRepository.class);
        subtitleTokenRepository = mock(SubtitleTokenRepository.class);
        TokenizerFactory tokenizerFactory = new TokenizerFactory(
                new OpenNlpTokenizer(), new KuromojiTokenizer(), new WhitespaceFallbackTokenizer());
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        persister = new VideoIngestionPersister(videoRepository, subtitleLineRepository, subtitleTokenRepository, tokenizerFactory, progressTracker);

        video = new Video("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", null);
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
    }

    @Test
    void persistsSubtitleLinesAndTokens() {
        List<VttCue> cues = List.of(new VttCue(1000, 4000, "Hello world"));

        persister.persistLines(1L, "en", cues);

        ArgumentCaptor<SubtitleLine> lineCaptor = ArgumentCaptor.forClass(SubtitleLine.class);
        verify(subtitleLineRepository).save(lineCaptor.capture());
        SubtitleLine savedLine = lineCaptor.getValue();
        assertEquals(0, savedLine.getSequence());
        assertEquals(1000, savedLine.getStartMs());
        assertEquals(4000, savedLine.getEndMs());
        assertEquals("Hello world", savedLine.getSourceText());

        ArgumentCaptor<SubtitleToken> tokenCaptor = ArgumentCaptor.forClass(SubtitleToken.class);
        verify(subtitleTokenRepository, times(2)).save(tokenCaptor.capture());
        List<String> surfaces = tokenCaptor.getAllValues().stream().map(SubtitleToken::getSurfaceText).toList();
        assertEquals(List.of("Hello", "world"), surfaces);
        List<String> normalized = tokenCaptor.getAllValues().stream().map(SubtitleToken::getNormalizedText).toList();
        assertEquals(List.of("hello", "world"), normalized);
    }

    @Test
    void markReadySetsStatusToReady() {
        persister.markReady(1L);
        assertEquals(VideoStatus.READY, video.getStatus());
    }

    @Test
    void markProcessingSetsStatusToProcessing() {
        persister.markProcessing(1L);
        assertEquals(VideoStatus.PROCESSING, video.getStatus());
    }

    @Test
    void markFailedSetsStatusAndFailureReason() {
        persister.markFailed(1L, FailureReason.NO_CAPTIONS_AVAILABLE, "no captions found");
        assertEquals(VideoStatus.FAILED, video.getStatus());
        assertEquals(FailureReason.NO_CAPTIONS_AVAILABLE, video.getFailureReason());
        assertEquals("no captions found", video.getFailureMessage());
    }
}
