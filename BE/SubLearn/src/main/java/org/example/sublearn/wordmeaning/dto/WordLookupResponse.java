package org.example.sublearn.wordmeaning.dto;

import java.util.List;

public record WordLookupResponse(
        String word,
        String contextualMeaning,
        String dictionaryMeaning,
        List<String> examples,
        boolean cached,
        String error
) {
}
