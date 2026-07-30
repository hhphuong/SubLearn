package org.example.sublearn.repository;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.DictionaryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DictionaryEntryRepository extends JpaRepository<DictionaryEntry, Long> {

    Optional<DictionaryEntry> findByUserAndNormalizedWordAndSourceLanguageAndTargetLanguage(
            AppUser user, String normalizedWord, String sourceLanguage, String targetLanguage);

    Page<DictionaryEntry> findByUser(AppUser user, Pageable pageable);

    Page<DictionaryEntry> findByUserAndTargetLanguage(AppUser user, String targetLanguage, Pageable pageable);

    Page<DictionaryEntry> findByUserAndDisplayWordContainingIgnoreCase(AppUser user, String search, Pageable pageable);

    Optional<DictionaryEntry> findByIdAndUser(Long id, AppUser user);
}
