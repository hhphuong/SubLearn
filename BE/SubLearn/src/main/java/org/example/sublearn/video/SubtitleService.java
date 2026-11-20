package org.example.sublearn.video;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleLineTranslation;
import org.example.sublearn.domain.SubtitleToken;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.job.ProcessingJobResponse;
import org.example.sublearn.job.ProcessingJobService;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.SubtitleLineTranslationRepository;
import org.example.sublearn.repository.SubtitleTokenRepository;
import org.example.sublearn.video.dto.SubtitleLineDto;
import org.example.sublearn.video.dto.SubtitlePageResponse;
import org.example.sublearn.video.dto.SubtitleTokenDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure read-only service for serving persisted subtitles and translations.
 * Never calls yt-dlp or Gemini directly; translations are created asynchronously
 * through RabbitMQ jobs (see {@link org.example.sublearn.job.VideoJobConsumer}).
 */
@Service
public class SubtitleService {

    private final VideoService videoService;
    private final SubtitleLineRepository subtitleLineRepository;
    private final SubtitleTokenRepository subtitleTokenRepository;
    private final SubtitleLineTranslationRepository subtitleLineTranslationRepository;
    private final ProcessingJobService processingJobService;

    public SubtitleService(VideoService videoService,
                            SubtitleLineRepository subtitleLineRepository,
                            SubtitleTokenRepository subtitleTokenRepository,
                            SubtitleLineTranslationRepository subtitleLineTranslationRepository,
                            ProcessingJobService processingJobService) {
        this.videoService = videoService;
        this.subtitleLineRepository = subtitleLineRepository;
        this.subtitleTokenRepository = subtitleTokenRepository;
        this.subtitleLineTranslationRepository = subtitleLineTranslationRepository;
        this.processingJobService = processingJobService;
    }

    @Transactional(readOnly = true)
    public SubtitlePageResponse getSubtitles(Long videoId, String targetLanguage, int page, int size) {
        Video video = videoService.getVideo(videoId);
        if (video.getStatus() != VideoStatus.READY) {
            throw ApiException.conflict("VIDEO_NOT_READY", "Video is not ready yet (status: " + video.getStatus() + ")");
        }

        Page<SubtitleLine> linePage = subtitleLineRepository.findByVideoOrderBySequenceAsc(video, PageRequest.of(page, size));
        List<SubtitleLine> lines = linePage.getContent();

        Map<Long, List<SubtitleToken>> tokensByLine = groupTokensByLine(lines);
        boolean sameLanguage = targetLanguage.equalsIgnoreCase(video.getSourceLanguage());

        Map<Long, String> translationsByLineId = sameLanguage || lines.isEmpty()
                ? Map.of()
                : loadTranslations(lines, targetLanguage);

        boolean translationAvailable = sameLanguage
                || lines.isEmpty()
                || translationsByLineId.size() == lines.size();

        ProcessingJobResponse activeTranslationJob = sameLanguage
                ? null
                : processingJobService.findLatestTranslationJob(videoId, targetLanguage)
                        .filter(j -> j.status() != JobStatus.COMPLETED)
                        .orElse(null);

        List<SubtitleLineDto> lineDtos = new ArrayList<>(lines.size());
        for (SubtitleLine line : lines) {
            String targetText = sameLanguage
                    ? line.getSourceText()
                    : translationsByLineId.getOrDefault(line.getId(), "");
            List<SubtitleTokenDto> tokenDtos = tokensByLine.getOrDefault(line.getId(), List.of()).stream()
                    .map(t -> new SubtitleTokenDto(t.getSequence(), t.getStartCharOffset(), t.getEndCharOffset(), t.getSurfaceText(), t.getTokenType()))
                    .toList();
            lineDtos.add(new SubtitleLineDto(line.getId(), line.getSequence(), line.getStartMs(), line.getEndMs(), line.getSourceText(), targetText, tokenDtos));
        }

        return new SubtitlePageResponse(page, size, linePage.getTotalElements(), video.getSourceLanguage(), targetLanguage,
                sameLanguage, lineDtos, translationAvailable, activeTranslationJob);
    }

    private Map<Long, List<SubtitleToken>> groupTokensByLine(List<SubtitleLine> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SubtitleToken>> grouped = new HashMap<>();
        for (SubtitleToken token : subtitleTokenRepository.findBySubtitleLineInOrderBySubtitleLineIdAscSequenceAsc(lines)) {
            grouped.computeIfAbsent(token.getSubtitleLine().getId(), id -> new ArrayList<>()).add(token);
        }
        return grouped;
    }

    private Map<Long, String> loadTranslations(List<SubtitleLine> lines, String targetLanguage) {
        Map<Long, String> byLineId = new HashMap<>();
        for (SubtitleLineTranslation translation : subtitleLineTranslationRepository.findBySubtitleLineInAndTargetLanguage(lines, targetLanguage)) {
            byLineId.put(translation.getSubtitleLine().getId(), translation.getTranslatedText());
        }
        return byLineId;
    }
}
