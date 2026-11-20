package org.example.sublearn.video.dto;

import org.example.sublearn.domain.VideoStatus;

public record VideoSubmitResponse(Long videoId, VideoStatus status, Long jobId) {

    public VideoSubmitResponse(Long videoId, VideoStatus status) {
        this(videoId, status, null);
    }
}
