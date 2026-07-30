package org.example.sublearn.video;

import org.example.sublearn.domain.TokenType;
import org.example.sublearn.video.dto.SubtitleLineDto;
import org.example.sublearn.video.dto.SubtitlePageResponse;
import org.example.sublearn.video.dto.SubtitleTokenDto;
import org.example.sublearn.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubtitleController.class)
@WithMockUser
class SubtitleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubtitleService subtitleService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void returnsSubtitleLinesInOrderWithSourceAndTargetTextAndTimestamps() throws Exception {
        SubtitlePageResponse response = new SubtitlePageResponse(0, 50, 2, "en", "vi", false, List.of(
                new SubtitleLineDto(0, 1000, 4000, "Hello world", "Xin chao the gioi",
                        List.of(new SubtitleTokenDto(0, 0, 5, "Hello", TokenType.WORD))),
                new SubtitleLineDto(1, 4000, 7000, "This is a test", "Day la mot bai kiem tra", List.of())
        ));
        given(subtitleService.getSubtitles(1L, "vi", 0, 50)).willReturn(response);

        mockMvc.perform(get("/api/videos/1/subtitles").param("targetLanguage", "vi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.targetLanguage").value("vi"))
                .andExpect(jsonPath("$.sameLanguage").value(false))
                .andExpect(jsonPath("$.totalLines").value(2))
                .andExpect(jsonPath("$.lines[0].sequence").value(0))
                .andExpect(jsonPath("$.lines[0].startMs").value(1000))
                .andExpect(jsonPath("$.lines[0].endMs").value(4000))
                .andExpect(jsonPath("$.lines[0].sourceText").value("Hello world"))
                .andExpect(jsonPath("$.lines[0].targetText").value("Xin chao the gioi"))
                .andExpect(jsonPath("$.lines[0].tokens[0].surfaceText").value("Hello"))
                .andExpect(jsonPath("$.lines[1].sequence").value(1))
                .andExpect(jsonPath("$.lines[1].startMs").value(4000));
    }
}
