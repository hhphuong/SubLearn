package org.example.sublearn.repository;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.DictionaryFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DictionaryFolderRepository extends JpaRepository<DictionaryFolder, Long> {

    List<DictionaryFolder> findByUserAndSourceLanguageOrderByCreatedAtDesc(AppUser user, String sourceLanguage);

    Optional<DictionaryFolder> findByIdAndUser(Long id, AppUser user);

    boolean existsByUserAndSourceLanguageAndNameIgnoreCase(AppUser user, String sourceLanguage, String name);

    long countByUser_Id(Long userId);
}
