package org.example.sublearn.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VttParserTest {

    @Test
    void parsesCleanLinesExtendingEachEndToTheNextLinesStart() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:04.000
                Hello world

                00:00:04.000 --> 00:00:07.500
                This is a test
                """;

        List<VttCue> cues = VttParser.parse(vtt);

        assertEquals(2, cues.size());
        // The first line's end is stretched to the second line's start so highlighting
        // never has a dead gap - not the raw cue's own (discarded) end timestamp.
        assertEquals(new VttCue(1000, 4000, "Hello world"), cues.get(0));
        assertEquals(4000, cues.get(1).startMs());
        assertEquals("This is a test", cues.get(1).text());
    }

    @Test
    void stripsInlineTagsAndSkipsConsecutiveDuplicateCues() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                <c>Hello</c> <00:00:01.500><c>world</c>

                00:00:02.000 --> 00:00:03.000
                Hello world

                00:00:05.000 --> 00:00:06.000
                Goodbye
                """;

        List<VttCue> cues = VttParser.parse(vtt);

        assertEquals(2, cues.size());
        assertEquals("Hello world", cues.get(0).text());
        assertEquals("Goodbye", cues.get(1).text());
    }

    @Test
    void mergesYoutubeRollingAutoCaptionWindowIntoOneLine() {
        // A trimmed real sample of yt-dlp's auto-caption output: a 2-line sliding window
        // where line 1 repeats the previous cue's (now-settled) line 2, and line 2 grows
        // word-by-word with per-word timestamp tags. The old parser treated every one of
        // these as its own line, producing a rapid, overlapping flash of fragments.
        String vtt = """
                WEBVTT
                Kind: captions
                Language: en

                00:00:07.520 --> 00:00:08.950 align:start position:0%
                [Music]
                this<00:00:07.720><c> program</c><00:00:08.160><c> is</c><00:00:08.280><c> brought</c><00:00:08.519><c> to</c><00:00:08.599><c> you</c><00:00:08.760><c> by</c>

                00:00:08.950 --> 00:00:08.960 align:start position:0%
                this program is brought to you by


                00:00:08.960 --> 00:00:11.870 align:start position:0%
                this program is brought to you by
                Stanford<00:00:09.519><c> University</c><00:00:10.480><c> please</c><00:00:10.800><c> visit</c><00:00:11.120><c> us</c><00:00:11.400><c> at</c>

                00:00:11.870 --> 00:00:11.880 align:start position:0%
                Stanford University please visit us at


                00:00:11.880 --> 00:00:20.509 align:start position:0%
                Stanford University please visit us at
                stanford.edu

                00:00:20.509 --> 00:00:20.519 align:start position:0%



                00:00:20.519 --> 00:00:26.070 align:start position:0%

                thank
                """;

        List<VttCue> cues = VttParser.parse(vtt);

        assertEquals(2, cues.size());
        assertEquals("this program is brought to you by Stanford University please visit us at stanford.edu",
                cues.get(0).text());
        assertEquals(7520, cues.get(0).startMs());
        assertEquals("thank", cues.get(1).text());
        // The long pause before "thank" (real silence, not just rolling-window noise)
        // correctly starts a new line instead of getting merged into the first one.
        assertTrue(cues.get(1).startMs() >= 20509);
    }

    @Test
    void filtersOutNonSpeechMarkersLikeMusicAndApplause() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                [Music]

                00:00:02.000 --> 00:00:03.000
                Hello there
                """;

        List<VttCue> cues = VttParser.parse(vtt);

        assertEquals(1, cues.size());
        assertEquals("Hello there", cues.get(0).text());
    }

    @Test
    void returnsEmptyListForBlankInput() {
        assertEquals(List.of(), VttParser.parse(""));
        assertEquals(List.of(), VttParser.parse(null));
    }
}
