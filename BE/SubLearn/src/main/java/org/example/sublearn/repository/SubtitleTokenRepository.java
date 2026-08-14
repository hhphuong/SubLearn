package org.example.sublearn.repository;

import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubtitleTokenRepository extends JpaRepository<SubtitleToken, Long> {

    List<SubtitleToken> findBySubtitleLineInOrderBySubtitleLineIdAscSequenceAsc(List<SubtitleLine> subtitleLines);
}
