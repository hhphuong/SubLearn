package org.example.sublearn.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VttParserTest {

    @Test
    void parsesCuesInOrderWithTimestampsAndText() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world

                00:00:04.000 --> 00:00:07.500
                This is a test
                """;

        List<VttCue> cues = VttParser.parse(vtt);

        assertEquals(2, cues.size());
        assertEquals(new VttCue(1000, 4000, "Hello world"), cues.get(0));
        assertEquals(new VttCue(4000, 7500, "This is a test"), cues.get(1));
    }

    @Test
    void stripsInlineTagsAndSkipsConsecutiveDuplicateCues() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                <c>Hello</c> <00:00:01.500><c>world</c>

                00:00:02.000 --> 00:00:03.000
                Hello world

                00:00:03.000 --> 00:00:04.000
                Goodbye
                """;

        List<VttCue> cues = VttParser.parse(vtt);

        assertEquals(2, cues.size());
        assertEquals("Hello world", cues.get(0).text());
        assertEquals("Goodbye", cues.get(1).text());
    }

    @Test
    void returnsEmptyListForBlankInput() {
        assertEquals(List.of(), VttParser.parse(""));
        assertEquals(List.of(), VttParser.parse(null));
    }
}
