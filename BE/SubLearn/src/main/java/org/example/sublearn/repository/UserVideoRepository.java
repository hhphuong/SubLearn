package org.example.sublearn.repository;

import org.example.sublearn.domain.UserVideo;
import org.example.sublearn.domain.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserVideoRepository extends JpaRepository<UserVideo, Long> {

    Optional<UserVideo> findByUser_IdAndVideo_Id(Long userId, Long videoId);

    @EntityGraph(attributePaths = "video")
    Page<UserVideo> findByUser_IdAndVideo_Status(Long userId, VideoStatus status, Pageable pageable);
}
