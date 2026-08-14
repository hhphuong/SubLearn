package org.example.sublearn.video;

import org.example.sublearn.video.dto.SubtitlePageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos/{videoId}/subtitles")
public class SubtitleController {

    private final SubtitleService subtitleService;

    public SubtitleController(SubtitleService subtitleService) {
        this.subtitleService = subtitleService;
    }

    @GetMapping
    public SubtitlePageResponse getSubtitles(@PathVariable Long videoId,
                                              @RequestParam String targetLanguage,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return subtitleService.getSubtitles(videoId, targetLanguage, page, size);
    }
}
