package org.example.sublearn.dictionary;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.dictionary.dto.DictionaryContextDto;
import org.example.sublearn.dictionary.dto.DictionaryEntryDetailResponse;
import org.example.sublearn.dictionary.dto.DictionaryEntrySummaryResponse;
import org.example.sublearn.dictionary.dto.DictionaryPageResponse;
import org.example.sublearn.dictionary.dto.SaveDictionaryRequest;
import org.example.sublearn.dictionary.dto.SaveDictionaryResponse;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.DictionaryEntry;
import org.example.sublearn.domain.DictionaryEntryContext;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.Video;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.VideoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DictionaryService {

    private final DictionaryEntryRepository dictionaryEntryRepository;
    private final AppUserRepository appUserRepository;
    private final VideoRepository videoRepository;
    private final SubtitleLineRepository subtitleLineRepository;

    public DictionaryService(DictionaryEntryRepository dictionaryEntryRepository, AppUserRepository appUserRepository,
                              VideoRepository videoRepository, SubtitleLineRepository subtitleLineRepository) {
        this.dictionaryEntryRepository = dictionaryEntryRepository;
        this.appUserRepository = appUserRepository;
        this.videoRepository = videoRepository;
        this.subtitleLineRepository = subtitleLineRepository;
    }

    @Transactional
    public SaveDictionaryResponse save(SaveDictionaryRequest request, Long userId) {
        AppUser user = appUserRepository.getReferenceById(userId);
        Video video = videoRepository.findById(request.videoId())
                .orElseThrow(() -> ApiException.notFound("VIDEO_NOT_FOUND", "Video not found: " + request.videoId()));
        SubtitleLine subtitleLine = request.subtitleLineId() == null ? null
                : subtitleLineRepository.findById(request.subtitleLineId()).orElse(null);

        String normalizedWord = request.word().trim().toLowerCase(Locale.ROOT);
        Optional<DictionaryEntry> existing = dictionaryEntryRepository
                .findByUserAndNormalizedWordAndSourceLanguageAndTargetLanguage(
                        user, normalizedWord, request.sourceLanguage(), request.targetLanguage());

        boolean wasNew = existing.isEmpty();
        DictionaryEntry entry;
        if (wasNew) {
            entry = new DictionaryEntry(user, normalizedWord, request.sourceLanguage(), request.targetLanguage(),
                    request.word(), request.contextualMeaning(), request.dictionaryMeaning(), request.examples());
            dictionaryEntryRepository.save(entry);
        } else {
            entry = existing.get();
            entry.refreshMeaning(request.word(), request.contextualMeaning(), request.dictionaryMeaning(), request.examples());
        }

        entry.addContext(new DictionaryEntryContext(entry, video, subtitleLine, request.sentenceSnapshot()));

        return new SaveDictionaryResponse(entry.getId(), wasNew);
    }

    @Transactional(readOnly = true)
    public DictionaryPageResponse list(Long userId, int page, int size, String targetLanguage, String search) {
        AppUser user = appUserRepository.getReferenceById(userId);
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<DictionaryEntry> entryPage;
        if (search != null && !search.isBlank()) {
            entryPage = dictionaryEntryRepository.findByUserAndDisplayWordContainingIgnoreCase(user, search, pageRequest);
        } else if (targetLanguage != null && !targetLanguage.isBlank()) {
            entryPage = dictionaryEntryRepository.findByUserAndTargetLanguage(user, targetLanguage, pageRequest);
        } else {
            entryPage = dictionaryEntryRepository.findByUser(user, pageRequest);
        }

        List<DictionaryEntrySummaryResponse> entries = entryPage.getContent().stream()
                .map(this::toSummary)
                .toList();
        return new DictionaryPageResponse(page, size, entryPage.getTotalElements(), entries);
    }

    @Transactional(readOnly = true)
    public DictionaryEntryDetailResponse getDetail(Long id, Long userId) {
        DictionaryEntry entry = getOwnedEntry(id, userId);
        List<DictionaryContextDto> contexts = entry.getContexts().stream()
                .map(c -> new DictionaryContextDto(c.getId(), c.getVideo().getId(),
                        c.getSubtitleLine() == null ? null : c.getSubtitleLine().getId(), c.getSentenceSnapshot(), c.getSavedAt()))
                .toList();
        return new DictionaryEntryDetailResponse(entry.getId(), entry.getDisplayWord(), entry.getSourceLanguage(),
                entry.getTargetLanguage(), entry.getContextualMeaning(), entry.getDictionaryMeaning(), entry.getExamples(),
                contexts, entry.getCreatedAt(), entry.getUpdatedAt());
    }

    @Transactional
    public void delete(Long id, Long userId) {
        DictionaryEntry entry = getOwnedEntry(id, userId);
        dictionaryEntryRepository.delete(entry);
    }

    private DictionaryEntry getOwnedEntry(Long id, Long userId) {
        AppUser user = appUserRepository.getReferenceById(userId);
        return dictionaryEntryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> ApiException.notFound("DICTIONARY_ENTRY_NOT_FOUND", "Dictionary entry not found: " + id));
    }

    private DictionaryEntrySummaryResponse toSummary(DictionaryEntry entry) {
        return new DictionaryEntrySummaryResponse(entry.getId(), entry.getDisplayWord(), entry.getSourceLanguage(),
                entry.getTargetLanguage(), entry.getContextualMeaning(), entry.getDictionaryMeaning(), entry.getExamples(),
                entry.getContexts().size(), entry.getCreatedAt(), entry.getUpdatedAt());
    }
}
