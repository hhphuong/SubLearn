package org.example.sublearn.dictionary;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.dictionary.dto.SaveDictionaryRequest;
import org.example.sublearn.dictionary.dto.SaveDictionaryResponse;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.DictionaryEntry;
import org.example.sublearn.domain.DictionaryFolder;
import org.example.sublearn.domain.Video;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.DictionaryFolderRepository;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictionaryServiceTest {

    private DictionaryEntryRepository dictionaryEntryRepository;
    private DictionaryFolderRepository dictionaryFolderRepository;
    private AppUserRepository appUserRepository;
    private VideoRepository videoRepository;
    private DictionaryService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        dictionaryEntryRepository = mock(DictionaryEntryRepository.class);
        dictionaryFolderRepository = mock(DictionaryFolderRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        videoRepository = mock(VideoRepository.class);
        SubtitleLineRepository subtitleLineRepository = mock(SubtitleLineRepository.class);
        service = new DictionaryService(dictionaryEntryRepository, dictionaryFolderRepository, appUserRepository,
                videoRepository, subtitleLineRepository);

        user = new AppUser("learner@example.com", "hash", "Learner");
        when(appUserRepository.getReferenceById(1L)).thenReturn(user);

        Video video = new Video("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", user);
        when(videoRepository.findById(10L)).thenReturn(Optional.of(video));
    }

    @Test
    void savingSameWordTwiceCreatesOneEntryWithTwoContextsInsteadOfADuplicateEntry() {
        SaveDictionaryRequest request = new SaveDictionaryRequest("Hello", "en", "vi", "a greeting", "hello (greeting)",
                List.of("Hello there!"), 10L, null, "Hello world", null);

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

    @Test
    void savingWithAFolderIdFilesTheEntryIntoThatFolder() {
        DictionaryFolder folder = new DictionaryFolder(user, "en", "Phrasal verbs");
        when(dictionaryFolderRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(folder));
        when(dictionaryEntryRepository.findByUserAndNormalizedWordAndSourceLanguageAndTargetLanguage(user, "give up", "en", "vi"))
                .thenReturn(Optional.empty());

        SaveDictionaryRequest request = new SaveDictionaryRequest("give up", "en", "vi", "to quit", "to stop trying",
                List.of("Don't give up!"), 10L, null, "Don't give up on your dreams", 5L);

        service.save(request, 1L);

        ArgumentCaptor<DictionaryEntry> captor = ArgumentCaptor.forClass(DictionaryEntry.class);
        verify(dictionaryEntryRepository).save(captor.capture());
        assertEquals(folder, captor.getValue().getFolder());
    }

    @Test
    void savingWithAFolderFromADifferentLanguageIsRejected() {
        DictionaryFolder japaneseFolder = new DictionaryFolder(user, "ja", "N5 words");
        when(dictionaryFolderRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(japaneseFolder));
        when(dictionaryEntryRepository.findByUserAndNormalizedWordAndSourceLanguageAndTargetLanguage(user, "hello", "en", "vi"))
                .thenReturn(Optional.empty());

        SaveDictionaryRequest request = new SaveDictionaryRequest("Hello", "en", "vi", "a greeting", "hello (greeting)",
                List.of("Hello there!"), 10L, null, "Hello world", 5L);

        assertThrows(ApiException.class, () -> service.save(request, 1L));
    }
}
