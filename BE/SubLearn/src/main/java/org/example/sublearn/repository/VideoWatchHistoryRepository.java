package org.example.sublearn.repository;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoWatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoWatchHistoryRepository extends JpaRepository<VideoWatchHistory, Long> {

    Optional<VideoWatchHistory> findByUserAndVideo(AppUser user, Video video);

    /** Caps "recently watched" at 50 entries - fetched whole and paged in memory since 50 rows is trivial. */
    List<VideoWatchHistory> findTop50ByUserOrderByLastWatchedAtDesc(AppUser user);
}
