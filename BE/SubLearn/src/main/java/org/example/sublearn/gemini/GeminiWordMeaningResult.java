package org.example.sublearn.gemini;

import java.util.List;

public record GeminiWordMeaningResult(String contextualMeaning, String dictionaryMeaning, List<String> examples) {
}
