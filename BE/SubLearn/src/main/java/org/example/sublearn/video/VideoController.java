package org.example.sublearn.video;

import jakarta.validation.Valid;
import org.example.sublearn.video.dto.VideoHistoryPageResponse;
import org.example.sublearn.video.dto.VideoStatusResponse;
import org.example.sublearn.video.dto.VideoSubmitRequest;
import org.example.sublearn.video.dto.VideoSubmitResponse;
import org.example.sublearn.video.dto.VideoSummaryResponse;
import org.example.sublearn.video.dto.VideoLibraryPageResponse;
import org.example.sublearn.video.dto.VideoProgressRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;
    private final VideoLibraryService videoLibraryService;

    public VideoController(VideoService videoService, VideoLibraryService videoLibraryService) {
        this.videoService = videoService;
        this.videoLibraryService = videoLibraryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoSubmitResponse submit(@Valid @RequestBody VideoSubmitRequest request, @AuthenticationPrincipal Long userId) {
        return videoService.submitVideo(request.youtubeUrl(), request.sourceLanguage(), request.targetLanguage(), userId);
    }

    // Registered before "/{videoId}" so this literal path always wins over the path-variable
    // pattern for the same segment position.
    @GetMapping("/history")
    public VideoHistoryPageResponse history(@AuthenticationPrincipal Long userId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "12") int size) {
        return videoService.getHistory(userId, page, size);
    }

    @GetMapping("/{videoId}/status")
    public VideoStatusResponse status(@PathVariable Long videoId) {
        return videoService.getStatus(videoId);
    }

    @GetMapping("/{videoId}")
    public VideoSummaryResponse summary(@PathVariable Long videoId, @AuthenticationPrincipal Long userId) {
        return videoService.getSummary(videoId, userId);
    }

    @GetMapping("/library")
    public VideoLibraryPageResponse library(@AuthenticationPrincipal Long userId,
                                             @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
                                             @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        return videoLibraryService.list(userId, page, size);
    }

    @PutMapping("/{videoId}/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProgress(@PathVariable Long videoId, @Valid @RequestBody VideoProgressRequest request,
                               @AuthenticationPrincipal Long userId) {
        videoLibraryService.updateProgress(userId, videoId, request.positionMs());
    }

    @PostMapping("/{videoId}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordWatch(@PathVariable Long videoId, @AuthenticationPrincipal Long userId) {
        videoService.recordWatch(videoId, userId);
    }

    @PostMapping("/{videoId}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoSubmitResponse reprocess(@PathVariable Long videoId) {
        return videoService.reprocess(videoId);
    }
}
