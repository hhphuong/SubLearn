package org.example.sublearn.video.dto;

import jakarta.validation.constraints.NotBlank;

public record VideoTranslationRequest(@NotBlank String targetLanguage) {
}
