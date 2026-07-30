package org.example.sublearn.dictionary;

import org.example.sublearn.dictionary.dto.SaveDictionaryRequest;
import org.example.sublearn.dictionary.dto.SaveDictionaryResponse;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.DictionaryEntry;
import org.example.sublearn.domain.Video;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictionaryServiceTest {

    private DictionaryEntryRepository dictionaryEntryRepository;
    private AppUserRepository appUserRepository;
    private VideoRepository videoRepository;
    private DictionaryService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        dictionaryEntryRepository = mock(DictionaryEntryRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        videoRepository = mock(VideoRepository.class);
        SubtitleLineRepository subtitleLineRepository = mock(SubtitleLineRepository.class);
        service = new DictionaryService(dictionaryEntryRepository, appUserRepository, videoRepository, subtitleLineRepository);

        user = new AppUser("learner@example.com", "hash", "Learner");
        when(appUserRepository.getReferenceById(1L)).thenReturn(user);

        Video video = new Video("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", user);
        when(videoRepository.findById(10L)).thenReturn(Optional.of(video));
    }

    @Test
    void savingSameWordTwiceCreatesOneEntryWithTwoContextsInsteadOfADuplicateEntry() {
        SaveDictionaryRequest request = new SaveDictionaryRequest("Hello", "en", "vi", "a greeting", "hello (greeting)",
                List.of("Hello there!"), 10L, null, "Hello world");

        when(dictionaryEntryRepository.findByUserAndNormalizedWordAndSourceLanguageAndTargetLanguage(user, "hello", "en", "vi"))
                .thenReturn(Optional.empty());

        SaveDictionaryResponse first = service.save(request, 1L);
        assertTrue(first.wasNew());

        ArgumentCaptor<DictionaryEntry> captor = ArgumentCaptor.forClass(DictionaryEntry.class);
        verify(dictionaryEntryRepository).save(captor.capture());
        DictionaryEntry savedEntry = captor.getValue();
        assertEquals(1, savedEntry.getContexts().size());

        when(dictionaryEntryRepository.findByUserAndNormalizedWordAndSourceLanguageAndTargetLanguage(user, "hello", "en", "vi"))
                .thenReturn(Optional.of(savedEntry));

        SaveDictionaryResponse second = service.save(request, 1L);
        assertFalse(second.wasNew());
        assertEquals(2, savedEntry.getContexts().size());

        verify(dictionaryEntryRepository, times(1)).save(any());
    }
}
