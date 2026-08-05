package org.example.sublearn.repository;

import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByYoutubeVideoId(String youtubeVideoId);

    List<Video> findByStatusIn(List<VideoStatus> statuses);
}
