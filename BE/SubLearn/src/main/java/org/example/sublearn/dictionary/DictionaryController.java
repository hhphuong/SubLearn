package org.example.sublearn.dictionary;

import jakarta.validation.Valid;
import org.example.sublearn.dictionary.dto.CreateDictionaryFolderRequest;
import org.example.sublearn.dictionary.dto.DictionaryEntryDetailResponse;
import org.example.sublearn.dictionary.dto.DictionaryFolderResponse;
import org.example.sublearn.dictionary.dto.DictionaryPageResponse;
import org.example.sublearn.dictionary.dto.SaveDictionaryRequest;
import org.example.sublearn.dictionary.dto.SaveDictionaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dictionary")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @PostMapping
    public SaveDictionaryResponse save(@Valid @RequestBody SaveDictionaryRequest request, @AuthenticationPrincipal Long userId) {
        return dictionaryService.save(request, userId);
    }

    @GetMapping
    public DictionaryPageResponse list(@AuthenticationPrincipal Long userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String sourceLanguage,
                                        @RequestParam(required = false) String targetLanguage,
                                        @RequestParam(required = false) Long folderId,
                                        @RequestParam(required = false) String search) {
        return dictionaryService.list(userId, page, size, sourceLanguage, targetLanguage, folderId, search);
    }

    @GetMapping("/{id}")
    public DictionaryEntryDetailResponse getDetail(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return dictionaryService.getDetail(id, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        dictionaryService.delete(id, userId);
    }

    @GetMapping("/folders")
    public List<DictionaryFolderResponse> listFolders(@AuthenticationPrincipal Long userId,
                                                        @RequestParam String sourceLanguage) {
        return dictionaryService.listFolders(userId, sourceLanguage);
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryFolderResponse createFolder(@Valid @RequestBody CreateDictionaryFolderRequest request,
                                                  @AuthenticationPrincipal Long userId) {
        return dictionaryService.createFolder(request, userId);
    }

    @DeleteMapping("/folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        dictionaryService.deleteFolder(id, userId);
    }
}
