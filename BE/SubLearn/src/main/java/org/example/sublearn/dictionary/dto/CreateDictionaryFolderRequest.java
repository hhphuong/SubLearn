package org.example.sublearn.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDictionaryFolderRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String sourceLanguage
) {
}
