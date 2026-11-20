package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.gemini.GeminiClient;
import org.example.sublearn.gemini.GeminiUnavailableException;
import org.example.sublearn.nlp.TokenizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VideoIngestionService {

    private static final Logger log = LoggerFactory.getLogger(VideoIngestionService.class);
    static final int TRANSLATION_BATCH_SIZE = 50;

    private final YtDlpClient ytDlpClient;
    private final VideoIngestionPersister persister;
    private final VideoProgressTracker progressTracker;
    private final GeminiClient geminiClient;

    public VideoIngestionService(YtDlpClient ytDlpClient, VideoIngestionPersister persister,
                                  VideoProgressTracker progressTracker, GeminiClient geminiClient) {
        this.ytDlpClient = ytDlpClient;
        this.persister = persister;
        this.progressTracker = progressTracker;
        this.geminiClient = geminiClient;
    }

    @Async("ingestionTaskExecutor")
    public void ingest(Long videoId, String youtubeVideoId, String requestedSourceLanguage) {
        ingestInternal(videoId, youtubeVideoId, requestedSourceLanguage, null, null);
    }

    @Async("ingestionTaskExecutor")
    public void ingest(Long videoId, String youtubeVideoId, String requestedSourceLanguage,
                       String targetLanguage, Long requestedByUserId) {
        ingestInternal(videoId, youtubeVideoId, requestedSourceLanguage, targetLanguage, requestedByUserId);
    }

    private void ingestInternal(Long videoId, String youtubeVideoId, String requestedSourceLanguage,
                                String targetLanguage, Long requestedByUserId) {
        persister.markProcessing(videoId);
        try {
            YtDlpResult result = ytDlpClient.fetchCaptionsAndMetadata(videoId, youtubeVideoId, requestedSourceLanguage);
            List<VttCue> cues = VttParser.parse(result.vttContent());
            progressTracker.beginSaving(videoId, result.title(), result.durationSeconds(), result.sourceLanguage(), cues.size());

            boolean needsTranslation = targetLanguage != null && !targetLanguage.isBlank()
                    && !targetLanguage.equalsIgnoreCase(result.sourceLanguage());
            List<String> translatedTexts = needsTranslation && !cues.isEmpty()
                    ? translateCues(cues, result.sourceLanguage(), targetLanguage)
                    : null;

            persister.persistLines(videoId, result.sourceLanguage(), cues, needsTranslation ? targetLanguage : null, translatedTexts);
            persister.markReady(videoId, requestedByUserId);
        } catch (YtDlpProcessException e) {
            log.info("Ingestion failed for video {}: {}", videoId, e.getMessage());
            persister.markFailed(videoId, e.getFailureReason(), e.getMessage());
        } catch (GeminiUnavailableException e) {
            log.warn("Translation failed during ingestion for video {}: {}", videoId, e.getMessage());
            persister.markFailed(videoId, FailureReason.TRANSLATION_FAILED, e.getMessage());
        } catch (TokenizationException e) {
            log.warn("Tokenization failed for video {}", videoId, e);
            persister.markFailed(videoId, FailureReason.TOKENIZATION_FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected ingestion failure for video {}", videoId, e);
            persister.markFailed(videoId, FailureReason.UNKNOWN_ERROR, e.getMessage());
        }
    }

    private List<String> translateCues(List<VttCue> cues, String sourceLanguage, String targetLanguage) {
        List<String> allTranslations = new ArrayList<>(cues.size());
        for (int start = 0; start < cues.size(); start += TRANSLATION_BATCH_SIZE) {
            int end = Math.min(start + TRANSLATION_BATCH_SIZE, cues.size());
            List<String> batchTexts = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                batchTexts.add(cues.get(i).text());
            }
            allTranslations.addAll(geminiClient.translateLines(batchTexts, sourceLanguage, targetLanguage));
        }
        return allTranslations;
    }
}
