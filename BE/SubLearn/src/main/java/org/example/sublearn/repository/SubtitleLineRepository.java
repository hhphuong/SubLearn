package org.example.sublearn.repository;

import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubtitleLineRepository extends JpaRepository<SubtitleLine, Long> {

    Page<SubtitleLine> findByVideoOrderBySequenceAsc(Video video, Pageable pageable);

    List<SubtitleLine> findByVideoOrderBySequenceAsc(Video video);

    long countByVideo(Video video);

    void deleteByVideo(Video video);
}
