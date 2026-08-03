package org.example.sublearn.video.dto;

import jakarta.validation.constraints.NotBlank;

public record VideoSubmitRequest(
        @NotBlank String youtubeUrl,
        @NotBlank String sourceLanguage
) {
}
