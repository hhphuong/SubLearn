package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleLineTranslation;
import org.example.sublearn.domain.SubtitleToken;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.nlp.KuromojiTokenizer;
import org.example.sublearn.nlp.OpenNlpTokenizer;
import org.example.sublearn.nlp.TokenizerFactory;
import org.example.sublearn.nlp.WhitespaceFallbackTokenizer;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.SubtitleLineTranslationRepository;
import org.example.sublearn.repository.SubtitleTokenRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.video.VideoLibraryService;
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
    private SubtitleLineTranslationRepository subtitleLineTranslationRepository;
    private VideoLibraryService videoLibraryService;
    private VideoIngestionPersister persister;
    private Video video;

    @BeforeEach
    void setUp() {
        videoRepository = mock(VideoRepository.class);
        subtitleLineRepository = mock(SubtitleLineRepository.class);
        subtitleTokenRepository = mock(SubtitleTokenRepository.class);
        subtitleLineTranslationRepository = mock(SubtitleLineTranslationRepository.class);
        videoLibraryService = mock(VideoLibraryService.class);
        TokenizerFactory tokenizerFactory = new TokenizerFactory(
                new OpenNlpTokenizer(), new KuromojiTokenizer(), new WhitespaceFallbackTokenizer());
        VideoProgressTracker progressTracker = mock(VideoProgressTracker.class);
        persister = new VideoIngestionPersister(videoRepository, subtitleLineRepository, subtitleTokenRepository,
                subtitleLineTranslationRepository, tokenizerFactory, progressTracker, videoLibraryService);

        video = new Video("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", null);
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
    }

    @Test
    void persistsSubtitleLinesTokensAndTranslations() {
        List<VttCue> cues = List.of(new VttCue(1000, 4000, "Hello world"));

        persister.persistLines(1L, "en", cues, "vi", List.of("Xin chào thế giới"));

        ArgumentCaptor<SubtitleLine> lineCaptor = ArgumentCaptor.forClass(SubtitleLine.class);
        verify(subtitleLineRepository).save(lineCaptor.capture());
        SubtitleLine savedLine = lineCaptor.getValue();
        assertEquals(0, savedLine.getSequence());
        assertEquals(1000, savedLine.getStartMs());
        assertEquals(4000, savedLine.getEndMs());
        assertEquals("Hello world", savedLine.getSourceText());

        ArgumentCaptor<SubtitleLineTranslation> translationCaptor = ArgumentCaptor.forClass(SubtitleLineTranslation.class);
        verify(subtitleLineTranslationRepository).save(translationCaptor.capture());
        assertEquals("vi", translationCaptor.getValue().getTargetLanguage());
        assertEquals("Xin chào thế giới", translationCaptor.getValue().getTranslatedText());

        ArgumentCaptor<SubtitleToken> tokenCaptor = ArgumentCaptor.forClass(SubtitleToken.class);
        verify(subtitleTokenRepository, times(2)).save(tokenCaptor.capture());
        List<String> surfaces = tokenCaptor.getAllValues().stream().map(SubtitleToken::getSurfaceText).toList();
        assertEquals(List.of("Hello", "world"), surfaces);
        List<String> normalized = tokenCaptor.getAllValues().stream().map(SubtitleToken::getNormalizedText).toList();
        assertEquals(List.of("hello", "world"), normalized);
    }

    @Test
    void markReadySetsStatusToReadyAndAddsToLibrary() {
        persister.markReady(1L, 9L);
        assertEquals(VideoStatus.READY, video.getStatus());
        verify(videoLibraryService).addToLibrary(9L, video);
    }

    @Test
    void markProcessingSetsStatusToProcessing() {
        persister.markProcessing(1L);
        assertEquals(VideoStatus.PROCESSING, video.getStatus());
    }

    @Test
    void markFailedSetsStatusAndClearsSubtitles() {
        persister.markFailed(1L, FailureReason.NO_CAPTIONS_AVAILABLE, "no captions found");
        assertEquals(VideoStatus.FAILED, video.getStatus());
        assertEquals(FailureReason.NO_CAPTIONS_AVAILABLE, video.getFailureReason());
        assertEquals("no captions found", video.getFailureMessage());
        verify(subtitleLineRepository).deleteByVideo(video);
    }
}
