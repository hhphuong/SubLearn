package org.example.sublearn.repository;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.DictionaryEntry;
import org.example.sublearn.domain.DictionaryFolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DictionaryEntryRepository extends JpaRepository<DictionaryEntry, Long> {

    Optional<DictionaryEntry> findByUserAndNormalizedWordAndSourceLanguageAndTargetLanguage(
            AppUser user, String normalizedWord, String sourceLanguage, String targetLanguage);

    /**
     * Every filter is optional (pass null to skip it) - kept as one query instead of a
     * derived-method per filter combination since the dictionary page filters by any mix of
     * source language, target language, folder, and free-text search.
     */
    @Query("""
            SELECT e FROM DictionaryEntry e WHERE e.user = :user
            AND (:sourceLanguage IS NULL OR e.sourceLanguage = :sourceLanguage)
            AND (:targetLanguage IS NULL OR e.targetLanguage = :targetLanguage)
            AND (:folderId IS NULL OR e.folder.id = :folderId)
            AND (:search IS NULL OR LOWER(e.displayWord) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<DictionaryEntry> search(@Param("user") AppUser user, @Param("sourceLanguage") String sourceLanguage,
                                  @Param("targetLanguage") String targetLanguage, @Param("folderId") Long folderId,
                                  @Param("search") String search, Pageable pageable);

    Optional<DictionaryEntry> findByIdAndUser(Long id, AppUser user);

    long countByFolder(DictionaryFolder folder);

    long countByUser_Id(Long userId);
}
