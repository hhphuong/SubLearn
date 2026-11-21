package org.example.sublearn.repository;

import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByYoutubeVideoId(String youtubeVideoId);

    List<Video> findByStatusIn(List<VideoStatus> statuses);

    long countByStatus(VideoStatus status);

    long countByCreatedAtAfter(Instant after);

    long countByStatusAndCreatedAtAfter(VideoStatus status, Instant after);

    long countByRequestedByUser_Id(Long userId);

    @Query("SELECT v.createdAt FROM Video v WHERE (:since IS NULL OR v.createdAt >= :since)")
    List<Instant> findCreatedAtSince(@Param("since") Instant since);
}
