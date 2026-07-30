package org.example.sublearn.wordmeaning;

import jakarta.validation.Valid;
import org.example.sublearn.wordmeaning.dto.WordLookupRequest;
import org.example.sublearn.wordmeaning.dto.WordLookupResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/words")
public class WordMeaningController {

    private final WordMeaningService wordMeaningService;

    public WordMeaningController(WordMeaningService wordMeaningService) {
        this.wordMeaningService = wordMeaningService;
    }

    @PostMapping("/lookup")
    public WordLookupResponse lookup(@Valid @RequestBody WordLookupRequest request) {
        return wordMeaningService.lookup(request);
    }
}
