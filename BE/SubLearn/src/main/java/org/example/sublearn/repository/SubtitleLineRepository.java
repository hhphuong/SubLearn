package org.example.sublearn.repository;

import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubtitleLineRepository extends JpaRepository<SubtitleLine, Long> {

    Page<SubtitleLine> findByVideoOrderBySequenceAsc(Video video, Pageable pageable);

    long countByVideo(Video video);
}
