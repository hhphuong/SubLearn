package org.example.sublearn.repository;

import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleLineTranslation;
import org.example.sublearn.domain.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubtitleLineTranslationRepository extends JpaRepository<SubtitleLineTranslation, Long> {

    Optional<SubtitleLineTranslation> findBySubtitleLineAndTargetLanguage(SubtitleLine subtitleLine, String targetLanguage);

    List<SubtitleLineTranslation> findBySubtitleLineInAndTargetLanguage(List<SubtitleLine> subtitleLines, String targetLanguage);

    long countBySubtitleLine_VideoAndTargetLanguage(Video video, String targetLanguage);

    void deleteBySubtitleLine_VideoAndTargetLanguage(Video video, String targetLanguage);
}
