package org.example.sublearn.dictionary;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.dictionary.dto.CreateDictionaryFolderRequest;
import org.example.sublearn.dictionary.dto.DictionaryContextDto;
import org.example.sublearn.dictionary.dto.DictionaryEntryDetailResponse;
import org.example.sublearn.dictionary.dto.DictionaryEntrySummaryResponse;
import org.example.sublearn.dictionary.dto.DictionaryFolderResponse;
import org.example.sublearn.dictionary.dto.DictionaryPageResponse;
import org.example.sublearn.dictionary.dto.SaveDictionaryRequest;
import org.example.sublearn.dictionary.dto.SaveDictionaryResponse;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.DictionaryEntry;
import org.example.sublearn.domain.DictionaryEntryContext;
import org.example.sublearn.domain.DictionaryFolder;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.Video;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.DictionaryFolderRepository;
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
    private final DictionaryFolderRepository dictionaryFolderRepository;
    private final AppUserRepository appUserRepository;
    private final VideoRepository videoRepository;
    private final SubtitleLineRepository subtitleLineRepository;

    public DictionaryService(DictionaryEntryRepository dictionaryEntryRepository,
                              DictionaryFolderRepository dictionaryFolderRepository, AppUserRepository appUserRepository,
                              VideoRepository videoRepository, SubtitleLineRepository subtitleLineRepository) {
        this.dictionaryEntryRepository = dictionaryEntryRepository;
        this.dictionaryFolderRepository = dictionaryFolderRepository;
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

        // Only touch the folder when the caller actually picked one - re-saving/updating a
        // word's meaning shouldn't silently evict it from a folder it was already filed in.
        if (request.folderId() != null) {
            entry.moveToFolder(getOwnedFolder(request.folderId(), user, request.sourceLanguage()));
        }

        entry.addContext(new DictionaryEntryContext(entry, video, subtitleLine, request.sentenceSnapshot()));

        return new SaveDictionaryResponse(entry.getId(), wasNew);
    }

    @Transactional(readOnly = true)
    public DictionaryPageResponse list(Long userId, int page, int size, String sourceLanguage, String targetLanguage,
                                        Long folderId, String search) {
        AppUser user = appUserRepository.getReferenceById(userId);
        PageRequest pageRequest = PageRequest.of(page, size);

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String normalizedSourceLanguage = (sourceLanguage == null || sourceLanguage.isBlank()) ? null : sourceLanguage;
        String normalizedTargetLanguage = (targetLanguage == null || targetLanguage.isBlank()) ? null : targetLanguage;

        Page<DictionaryEntry> entryPage = dictionaryEntryRepository.search(
                user, normalizedSourceLanguage, normalizedTargetLanguage, folderId, normalizedSearch, pageRequest);

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
        DictionaryFolder folder = entry.getFolder();
        return new DictionaryEntryDetailResponse(entry.getId(), entry.getDisplayWord(), entry.getSourceLanguage(),
                entry.getTargetLanguage(), folder == null ? null : folder.getId(), folder == null ? null : folder.getName(),
                entry.getContextualMeaning(), entry.getDictionaryMeaning(), entry.getExamples(),
                contexts, entry.getCreatedAt(), entry.getUpdatedAt());
    }

    @Transactional
    public void delete(Long id, Long userId) {
        DictionaryEntry entry = getOwnedEntry(id, userId);
        dictionaryEntryRepository.delete(entry);
    }

    /** Newest first, so the FE can default-select "the folder I just created" with no extra logic. */
    @Transactional(readOnly = true)
    public List<DictionaryFolderResponse> listFolders(Long userId, String sourceLanguage) {
        AppUser user = appUserRepository.getReferenceById(userId);
        return dictionaryFolderRepository.findByUserAndSourceLanguageOrderByCreatedAtDesc(user, sourceLanguage).stream()
                .map(f -> new DictionaryFolderResponse(f.getId(), f.getName(), f.getSourceLanguage(),
                        (int) dictionaryEntryRepository.countByFolder(f), f.getCreatedAt()))
                .toList();
    }

    @Transactional
    public DictionaryFolderResponse createFolder(CreateDictionaryFolderRequest request, Long userId) {
        AppUser user = appUserRepository.getReferenceById(userId);
        String name = request.name().trim();
        if (dictionaryFolderRepository.existsByUserAndSourceLanguageAndNameIgnoreCase(user, request.sourceLanguage(), name)) {
            throw ApiException.conflict("FOLDER_NAME_TAKEN", "You already have a folder named \"" + name + "\" for this language");
        }
        DictionaryFolder folder = new DictionaryFolder(user, request.sourceLanguage(), name);
        dictionaryFolderRepository.save(folder);
        return new DictionaryFolderResponse(folder.getId(), folder.getName(), folder.getSourceLanguage(), 0, folder.getCreatedAt());
    }

    @Transactional
    public void deleteFolder(Long id, Long userId) {
        AppUser user = appUserRepository.getReferenceById(userId);
        DictionaryFolder folder = dictionaryFolderRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> ApiException.notFound("FOLDER_NOT_FOUND", "Folder not found: " + id));
        // Entries in the folder aren't deleted, just unfiled - folder_id is ON DELETE SET NULL.
        dictionaryFolderRepository.delete(folder);
    }

    private DictionaryEntry getOwnedEntry(Long id, Long userId) {
        AppUser user = appUserRepository.getReferenceById(userId);
        return dictionaryEntryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> ApiException.notFound("DICTIONARY_ENTRY_NOT_FOUND", "Dictionary entry not found: " + id));
    }

    private DictionaryFolder getOwnedFolder(Long folderId, AppUser user, String expectedSourceLanguage) {
        DictionaryFolder folder = dictionaryFolderRepository.findByIdAndUser(folderId, user)
                .orElseThrow(() -> ApiException.notFound("FOLDER_NOT_FOUND", "Folder not found: " + folderId));
        if (!folder.getSourceLanguage().equals(expectedSourceLanguage)) {
            throw ApiException.badRequest("FOLDER_LANGUAGE_MISMATCH",
                    "Folder \"" + folder.getName() + "\" is not for language " + expectedSourceLanguage);
        }
        return folder;
    }

    private DictionaryEntrySummaryResponse toSummary(DictionaryEntry entry) {
        DictionaryFolder folder = entry.getFolder();
        return new DictionaryEntrySummaryResponse(entry.getId(), entry.getDisplayWord(), entry.getSourceLanguage(),
                entry.getTargetLanguage(), folder == null ? null : folder.getId(), folder == null ? null : folder.getName(),
                entry.getContextualMeaning(), entry.getDictionaryMeaning(),
                entry.getExamples(), entry.getContexts().size(), entry.getCreatedAt(), entry.getUpdatedAt());
    }
}
