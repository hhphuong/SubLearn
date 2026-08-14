package org.example.sublearn.ingestion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
    void breaksOnJapaneseSentenceEndingPunctuationEvenWithoutALongPause() {
        // YouTube's Japanese auto-captions - unlike English ones - already contain real
        // sentence-ending punctuation (。！？) in the machine-generated stream. The gap
        // between these two lines is only 50ms - far under the pause threshold - so this
        // only splits into two sentences because of the punctuation-based rule.
        String vtt = """
                WEBVTT

                00:00:00.000 --> 00:00:01.000
                はい、皆さんこんにちは。

                00:00:01.050 --> 00:00:02.000
                今日は良い天気です。
                """;

        List<VttCue> cues = VttParser.parse(vtt);

        assertEquals(2, cues.size());
        assertEquals("はい、皆さんこんにちは。", cues.get(0).text());
        assertEquals("今日は良い天気です。", cues.get(1).text());
    }

    @Test
    void mergesYoutubeRollingAutoCaptionWindowDedupingRepeatsAndSplittingOnRealPauses() {
        // A trimmed real sample of yt-dlp's auto-caption output: a 2-line sliding window
        // where line 1 repeats the previous cue's (now-settled) line 2, and line 2 grows
        // word-by-word with per-word timestamp tags. The old (pre-dedup) parser treated
        // every one of these as its own line, producing a rapid, overlapping flash of
        // fragments - this verifies the rolling repeats are correctly merged/deduped.
        //
        // There's a genuine ~961ms gap between "University" and "please" (a real breath
        // between phrases), which is above the no-punctuation pause threshold, so this
        // correctly splits into two phrases there rather than one long run-on line.
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

        assertEquals(3, cues.size());
        assertEquals("this program is brought to you by Stanford University", cues.get(0).text());
        assertEquals(7520, cues.get(0).startMs());
        assertEquals("please visit us at stanford.edu", cues.get(1).text());
        assertEquals("thank", cues.get(2).text());
        // The long pause before "thank" (real silence, not just rolling-window noise)
        // correctly starts a new line instead of getting merged into the previous one.
        assertTrue(cues.get(2).startMs() >= 20509);
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

    @Test
    void breaksOnNaturalPausesRatherThanWordCountWhenTranscriptHasNoPunctuation() {
        // A realistic English auto-caption has no punctuation at all, so the only genuine
        // "by idea" signal available is timing: most word-to-word gaps are small (normal
        // speaking pace), with a handful of larger gaps marking real clause/sentence breaks.
        // This must split at exactly those larger gaps - never at a fixed word count - even
        // though each resulting chunk is well under the word-count safety net.
        List<TimedWordSpec> words = new ArrayList<>();
        long ts = 0;
        String[] firstClause = {"we", "went", "to", "the", "market", "this", "morning"};
        String[] secondClause = {"then", "we", "came", "home", "and", "made", "lunch"};
        String[] thirdClause = {"it", "was", "a", "really", "good", "day"};
        for (String w : firstClause) {
            words.add(new TimedWordSpec(w, ts));
            ts += 300;
        }
        ts += 700; // a real pause between clauses - just over the 600ms threshold
        for (String w : secondClause) {
            words.add(new TimedWordSpec(w, ts));
            ts += 300;
        }
        ts += 700;
        for (String w : thirdClause) {
            words.add(new TimedWordSpec(w, ts));
            ts += 300;
        }

        List<VttCue> cues = VttParser.parse(buildTaggedVtt(words));

        assertEquals(3, cues.size());
        assertEquals("we went to the market this morning", cues.get(0).text());
        assertEquals("then we came home and made lunch", cues.get(1).text());
        assertEquals("it was a really good day", cues.get(2).text());
    }

    @Test
    void fallsBackToTheWordCountSafetyNetOnlyForATrulyContinuousPauseFreeStream() {
        // A continuous, unbroken narration (e.g. a meditation/ASMR video) with no real pause
        // between words and - since it's an English auto-caption - no punctuation either:
        // there is no sentence signal at all to split on. This is the rare case the
        // word-count safety net exists for, so it must still eventually fire rather than
        // growing into one unbroken wall of text.
        List<TimedWordSpec> words = new ArrayList<>();
        for (int i = 1; i <= 45; i++) {
            words.add(new TimedWordSpec("word" + i, (i - 1) * 400L));
        }

        List<VttCue> cues = VttParser.parse(buildTaggedVtt(words));

        assertEquals(3, cues.size());
        assertEquals(20, cues.get(0).text().split(" ").length);
        assertEquals(20, cues.get(1).text().split(" ").length);
        assertEquals(5, cues.get(2).text().split(" ").length);
        assertEquals("word1", cues.get(0).text().split(" ")[0]);
        assertEquals("word21", cues.get(1).text().split(" ")[0]);
        assertEquals("word41", cues.get(2).text().split(" ")[0]);
    }

    @Test
    void oneIncidentalStrayPunctuationMarkDoesNotFlipAWholeTranscriptToThePunctuatedThresholds() {
        // Observed in production: YouTube's ASR occasionally transcribes a spoken year as
        // e.g. "2065." with a trailing period - a single incidental mark - even in an
        // otherwise completely unpunctuated English auto-caption. Treating "has punctuation
        // ANYWHERE" as reason enough to use the loose punctuated-case thresholds (pause up to
        // 1800ms, 40-word cap) would silently disable pause-based splitting for the entire
        // rest of a long transcript - exactly the "cuts arbitrarily" bug this guards against.
        // 90 words, one stray "." (barely over a 1% density, far below the 2% classification
        // threshold), with four genuine ~700ms pauses spaced under the 20-word safety-net cap
        // that must still drive where lines actually break.
        List<TimedWordSpec> words = new ArrayList<>();
        long ts = 0;
        for (int i = 1; i <= 90; i++) {
            String text = (i == 45) ? "word45." : "word" + i;
            words.add(new TimedWordSpec(text, ts));
            ts += (i == 18 || i == 36 || i == 54 || i == 72) ? 700 : 300;
        }

        List<VttCue> cues = VttParser.parse(buildTaggedVtt(words));

        // 6, not 5: the stray "word45." mark still locally ends its own chunk right there (a
        // real sentence-ending mark is always a valid break point, incidental or not) - what
        // matters is that this single mark does NOT also disable pause-based splitting for
        // the rest of the transcript, which is confirmed by the four ~700ms pauses (at
        // word18|19, 36|37, 54|55 and 72|73) still driving their own, separate breaks as
        // expected, rather than falling back to the loose punctuated-case thresholds.
        assertEquals(6, cues.size());
        assertEquals("word1", cues.get(0).text().split(" ")[0]);
        assertEquals("word18", cues.get(0).text().split(" ")[cues.get(0).text().split(" ").length - 1]);
        assertEquals("word19", cues.get(1).text().split(" ")[0]);
        assertEquals("word36", cues.get(1).text().split(" ")[cues.get(1).text().split(" ").length - 1]);
        assertEquals("word37", cues.get(2).text().split(" ")[0]);
        assertEquals("word45.", cues.get(2).text().split(" ")[cues.get(2).text().split(" ").length - 1]);
        assertEquals("word46", cues.get(3).text().split(" ")[0]);
        assertEquals("word54", cues.get(3).text().split(" ")[cues.get(3).text().split(" ").length - 1]);
        assertEquals("word55", cues.get(4).text().split(" ")[0]);
        assertEquals("word72", cues.get(4).text().split(" ")[cues.get(4).text().split(" ").length - 1]);
        assertEquals("word73", cues.get(5).text().split(" ")[0]);
        assertEquals("word90", cues.get(5).text().split(" ")[cues.get(5).text().split(" ").length - 1]);
    }

    private record TimedWordSpec(String text, long timestampMs) {
    }

    /** Builds a single-cue VTT block with each word individually tagged at its given timestamp. */
    private static String buildTaggedVtt(List<TimedWordSpec> words) {
        StringBuilder line = new StringBuilder(words.get(0).text());
        for (int i = 1; i < words.size(); i++) {
            TimedWordSpec w = words.get(i);
            line.append(String.format("<00:00:%02d.%03d><c> %s</c>", w.timestampMs() / 1000, w.timestampMs() % 1000, w.text()));
        }
        return "WEBVTT\n\n00:00:00.000 --> 00:00:30.000\n" + line + "\n";
    }
}
