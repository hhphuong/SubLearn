package org.example.sublearn.video.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record VideoProgressRequest(
        @PositiveOrZero long positionMs
) {
}
