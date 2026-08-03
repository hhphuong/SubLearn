package org.example.sublearn.video;

import jakarta.validation.Valid;
import org.example.sublearn.video.dto.VideoStatusResponse;
import org.example.sublearn.video.dto.VideoSubmitRequest;
import org.example.sublearn.video.dto.VideoSubmitResponse;
import org.example.sublearn.video.dto.VideoSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoSubmitResponse submit(@Valid @RequestBody VideoSubmitRequest request, @AuthenticationPrincipal Long userId) {
        return videoService.submitVideo(request.youtubeUrl(), request.sourceLanguage(), userId);
    }

    @GetMapping("/{videoId}/status")
    public VideoStatusResponse status(@PathVariable Long videoId) {
        return videoService.getStatus(videoId);
    }

    @GetMapping("/{videoId}")
    public VideoSummaryResponse summary(@PathVariable Long videoId) {
        return videoService.getSummary(videoId);
    }
}
