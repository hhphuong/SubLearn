package org.example.sublearn.video;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.SubtitleLine;
import org.example.sublearn.domain.SubtitleLineTranslation;
import org.example.sublearn.domain.SubtitleToken;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.gemini.GeminiClient;
import org.example.sublearn.gemini.GeminiUnavailableException;
import org.example.sublearn.repository.SubtitleLineRepository;
import org.example.sublearn.repository.SubtitleLineTranslationRepository;
import org.example.sublearn.repository.SubtitleTokenRepository;
import org.example.sublearn.video.dto.SubtitleLineDto;
import org.example.sublearn.video.dto.SubtitlePageResponse;
import org.example.sublearn.video.dto.SubtitleTokenDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubtitleService {

    private static final Logger log = LoggerFactory.getLogger(SubtitleService.class);

    private final VideoService videoService;
    private final SubtitleLineRepository subtitleLineRepository;
    private final SubtitleTokenRepository subtitleTokenRepository;
    private final SubtitleLineTranslationRepository subtitleLineTranslationRepository;
    private final GeminiClient geminiClient;

    public SubtitleService(VideoService videoService, SubtitleLineRepository subtitleLineRepository,
                            SubtitleTokenRepository subtitleTokenRepository,
                            SubtitleLineTranslationRepository subtitleLineTranslationRepository,
                            GeminiClient geminiClient) {
        this.videoService = videoService;
        this.subtitleLineRepository = subtitleLineRepository;
        this.subtitleTokenRepository = subtitleTokenRepository;
        this.subtitleLineTranslationRepository = subtitleLineTranslationRepository;
        this.geminiClient = geminiClient;
    }

    @Transactional
    public SubtitlePageResponse getSubtitles(Long videoId, String targetLanguage, int page, int size) {
        Video video = videoService.getVideo(videoId);
        if (video.getStatus() != VideoStatus.READY) {
            throw ApiException.conflict("VIDEO_NOT_READY", "Video is not ready yet (status: " + video.getStatus() + ")");
        }

        Page<SubtitleLine> linePage = subtitleLineRepository.findByVideoOrderBySequenceAsc(video, PageRequest.of(page, size));
        List<SubtitleLine> lines = linePage.getContent();

        Map<Long, List<SubtitleToken>> tokensByLine = groupTokensByLine(lines);
        boolean sameLanguage = targetLanguage.equalsIgnoreCase(video.getSourceLanguage());
        Map<Long, String> translations = sameLanguage ? Map.of() : resolveTranslations(video, lines, targetLanguage);

        List<SubtitleLineDto> lineDtos = new ArrayList<>(lines.size());
        for (SubtitleLine line : lines) {
            String targetText = sameLanguage ? line.getSourceText() : translations.getOrDefault(line.getId(), line.getSourceText());
            List<SubtitleTokenDto> tokenDtos = tokensByLine.getOrDefault(line.getId(), List.of()).stream()
                    .map(t -> new SubtitleTokenDto(t.getSequence(), t.getStartCharOffset(), t.getEndCharOffset(), t.getSurfaceText(), t.getTokenType()))
                    .toList();
            lineDtos.add(new SubtitleLineDto(line.getId(), line.getSequence(), line.getStartMs(), line.getEndMs(), line.getSourceText(), targetText, tokenDtos));
        }

        return new SubtitlePageResponse(page, size, linePage.getTotalElements(), video.getSourceLanguage(), targetLanguage, sameLanguage, lineDtos);
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

    private Map<Long, String> resolveTranslations(Video video, List<SubtitleLine> lines, String targetLanguage) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> byLineId = new HashMap<>();
        for (SubtitleLineTranslation translation : subtitleLineTranslationRepository.findBySubtitleLineInAndTargetLanguage(lines, targetLanguage)) {
            byLineId.put(translation.getSubtitleLine().getId(), translation.getTranslatedText());
        }

        List<SubtitleLine> missing = lines.stream().filter(l -> !byLineId.containsKey(l.getId())).toList();
        if (missing.isEmpty()) {
            return byLineId;
        }

        try {
            List<String> sourceTexts = missing.stream().map(SubtitleLine::getSourceText).toList();
            List<String> translatedTexts = geminiClient.translateLines(sourceTexts, video.getSourceLanguage(), targetLanguage);
            for (int i = 0; i < missing.size(); i++) {
                SubtitleLine line = missing.get(i);
                String translatedText = translatedTexts.get(i);
                subtitleLineTranslationRepository.save(new SubtitleLineTranslation(line, targetLanguage, translatedText));
                byLineId.put(line.getId(), translatedText);
            }
        } catch (GeminiUnavailableException e) {
            log.warn("Translation unavailable for video {} -> {}, falling back to source text for {} lines",
                    video.getId(), targetLanguage, missing.size(), e);
            for (SubtitleLine line : missing) {
                byLineId.put(line.getId(), line.getSourceText());
            }
        }
        return byLineId;
    }
}
