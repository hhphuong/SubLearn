package org.example.sublearn.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses WebVTT into meaningful, sentence/phrase-sized lines.
 *
 * <p>YouTube auto-generated captions don't arrive as clean lines - each cue "rolls" a
 * 2-line sliding window: line 1 repeats whatever was line 2 of the previous cue (now
 * settled), line 2 grows word by word as speech continues, with each new word tagged
 * with its own timestamp ({@code word<HH:MM:SS.mmm><c> next</c>}). Treating each raw cue
 * as one subtitle line (the previous behavior) produced dozens of near-duplicate
 * fragments per second - a few words at a time, mixed with the tail of the previous
 * utterance.
 *
 * <p>This instead reconstructs a flat, word-level (timestamp, text) timeline from the
 * raw cues - de-duplicating settled repeats and reading each newly-tagged word's own
 * timestamp - and then re-chunks that timeline into lines at natural speech pauses
 * (capped by a max word count/duration so a long uninterrupted monologue still breaks
 * into readable pieces). Manual (human-authored) subtitles have no rolling/tags at all;
 * the same logic still works for them since each of their lines is simply never a repeat
 * of the previous one, so it flows through as its own single-token chunk unchanged.
 */
public final class VttParser {

    private static final Pattern TIMESTAMP_LINE = Pattern.compile(
            "(\\d{2}:)?(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(\\d{2}:)?(\\d{2}):(\\d{2})\\.(\\d{3})");
    private static final Pattern TAGGED_WORD = Pattern.compile(
            "<(?:(\\d{2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})><c>([^<]*)</c>");
    private static final Pattern INLINE_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern NON_SPEECH_MARKER = Pattern.compile("^\\[[^\\]]*]$");

    /**
     * Gap between words that marks a natural pause - starts a new line. Set high enough
     * that an ordinary mid-sentence breath (typically well under a second) doesn't fragment
     * a complete sentence into several short pieces; only a longer silence - usually a real
     * break between sentences or thoughts - crosses it. Used whenever the transcript has
     * punctuation to lean on (see {@link #NO_PUNCTUATION_PAUSE_THRESHOLD_MS} otherwise).
     */
    private static final long PAUSE_THRESHOLD_MS = 1800;
    /**
     * Hard safety net only when punctuation is present - real sentence/pause detection is
     * what actually decides where lines break in that case, so this is set loose enough
     * that a normal sentence never hits it. When a transcript has NO punctuation anywhere
     * (English auto-captions, or an unpunctuated manual track), there is no sentence
     * signal at all to fall back on, so {@link #NO_PUNCTUATION_MAX_WORDS_PER_LINE} applies
     * instead and is the actual, primary decider of line breaks in that case.
     */
    private static final int MAX_WORDS_PER_LINE = 40;
    private static final long MAX_LINE_DURATION_MS = 20000;
    /**
     * Pause threshold used instead of {@link #PAUSE_THRESHOLD_MS} when the whole transcript
     * has no punctuation anywhere to detect sentence ends with (English auto-captions, or an
     * unpunctuated manual track) - this, not a word count, is what must do the real work of
     * finding "by idea" breaks in that case. Measured against a real English auto-caption
     * transcript (a natural, conversationally-paced TED talk): word-to-word gaps have a
     * median of ~160ms with a long tail, and at this threshold essentially every break is a
     * genuine pause (the word-count safety net below almost never fires) while still landing
     * on a readable ~8-9 words per line on average - both a 1000ms and especially the old
     * 1800ms threshold let the word-count cap silently become the actual decider for a third
     * or more of all breaks, which is exactly the "cut arbitrarily, not by a real pause"
     * behavior this exists to avoid.
     */
    private static final long NO_PUNCTUATION_PAUSE_THRESHOLD_MS = 600;
    /**
     * Deliberately tighter than {@link #MAX_WORDS_PER_LINE} (40): that value is a rare
     * backstop for the punctuated case, where real sentence boundaries do almost all of the
     * work. Without punctuation, the {@link #NO_PUNCTUATION_PAUSE_THRESHOLD_MS} pause is the
     * only real signal, and some speech (fast, continuous talkers; podcasts) simply never has
     * a gap that crosses it for a long stretch - if this cap were also 40 words, that stretch
     * would ride all the way to a 40-word wall of text, which reads as "still not breaking at
     * the end of an idea" even though pause detection is technically working. This forces a
     * break at a shorter, still-readable length instead.
     */
    private static final int NO_PUNCTUATION_MAX_WORDS_PER_LINE = 20;
    private static final long NO_PUNCTUATION_MAX_LINE_DURATION_MS = 10000;
    /**
     * Minimum fraction of timeline tokens that must be a sentence-ending mark before a
     * transcript is treated as "punctuated". Real punctuated prose (manual captions, or
     * Japanese auto-captions) lands one every ~5-20 tokens (roughly 5%-20%); this is set far
     * below that so genuinely punctuated content is never misclassified, while still safely
     * excluding a transcript that only has one or two incidental marks in several thousand
     * words (see the "2065." example above).
     */
    private static final double PUNCTUATION_DENSITY_THRESHOLD = 0.02;
    /**
     * A sentence-ending mark is a far stronger "complete idea" signal than any pause
     * length, so it forces a break right after it even if the next word follows almost
     * immediately - this is the primary way lines are split, pauses are the fallback for
     * text that has no punctuation at all.
     *
     * <p>Manual/human subtitles always carry punctuation; English auto-captions never do
     * (no signal here, falls through to pause detection). Japanese (and other CJK)
     * auto-captions are the exception: YouTube's speech recognition inserts real 。/！/？
     * even in the machine-generated stream, so this must recognize those too, not just
     * ASCII . ! ? - otherwise Japanese lines fall back to pause/cap splitting for no reason
     * even though the source data already marks sentence boundaries.
     */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。！？][\"')\\]」』]?$");
    /** How long the very last line stays on screen, since there's no "next line" to bound it. */
    private static final long LAST_LINE_TAIL_MS = 2000;

    private record TimedWord(long timestampMs, String text) {
    }

    private VttParser() {
    }

    public static List<VttCue> parse(String vttContent) {
        List<TimedWord> timeline = extractWordTimeline(vttContent);
        return chunkIntoLines(timeline);
    }

    private static List<TimedWord> extractWordTimeline(String vttContent) {
        if (vttContent == null || vttContent.isBlank()) {
            return List.of();
        }
        String[] lines = vttContent.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        List<TimedWord> timeline = new ArrayList<>();
        String lastEmittedPlainText = null;
        int i = 0;
        while (i < lines.length) {
            Matcher matcher = TIMESTAMP_LINE.matcher(lines[i]);
            if (matcher.find()) {
                long startMs = toMillis(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
                i++;
                // A cue block's own content can include a blank/whitespace-only "line 1"
                // (nothing shown on the top display line) followed by real text - so blank
                // lines alone can't mark the end of a block. Only the next timestamp line
                // (the start of a new block) does; blank content lines just contribute
                // empty text, which is skipped naturally below.
                while (i < lines.length && !TIMESTAMP_LINE.matcher(lines[i]).find()) {
                    String rawLine = lines[i];
                    String plainText = INLINE_TAG.matcher(rawLine).replaceAll("").trim();
                    if (!plainText.isEmpty() && !plainText.equals(lastEmittedPlainText)
                            && !NON_SPEECH_MARKER.matcher(plainText).matches()) {
                        timeline.addAll(parseTaggedLine(startMs, rawLine));
                        lastEmittedPlainText = plainText;
                    }
                    i++;
                }
            } else {
                i++;
            }
        }
        return timeline;
    }

    /** Splits a single raw cue line into (word, timestamp) tokens using its {@code <c>} tags, if any. */
    private static List<TimedWord> parseTaggedLine(long lineStartMs, String rawLine) {
        List<TimedWord> tokens = new ArrayList<>();
        Matcher tagMatcher = TAGGED_WORD.matcher(rawLine);
        int firstTagStart = tagMatcher.find() ? tagMatcher.start() : -1;

        String leadingRaw = firstTagStart >= 0 ? rawLine.substring(0, firstTagStart) : rawLine;
        String leading = INLINE_TAG.matcher(leadingRaw).replaceAll("").trim();
        if (!leading.isEmpty()) {
            tokens.add(new TimedWord(lineStartMs, leading));
        }
        if (firstTagStart >= 0) {
            tagMatcher.reset();
            while (tagMatcher.find()) {
                long ts = toMillis(tagMatcher.group(1), tagMatcher.group(2), tagMatcher.group(3), tagMatcher.group(4));
                String word = INLINE_TAG.matcher(tagMatcher.group(5)).replaceAll("").trim();
                if (!word.isEmpty()) {
                    tokens.add(new TimedWord(ts, word));
                }
            }
        }
        return tokens;
    }

    private static List<VttCue> chunkIntoLines(List<TimedWord> timeline) {
        if (timeline.isEmpty()) {
            return List.of();
        }
        // A transcript with no real sentence-ending punctuation has no signal at all for
        // SENTENCE_END to key off; fall back to a pause threshold tuned for that case instead.
        // Deliberately a density check, not "any match at all": YouTube's ASR occasionally
        // emits an incidental stray period on its own (observed in production: a spoken year
        // like "2065" transcribed as "2065.") even in an otherwise completely unpunctuated
        // English auto-caption - a single such stray mark must not flip an entire ~3000-word
        // transcript into using the punctuated-case thresholds, which is what an "any match"
        // check would do and is exactly the "cuts arbitrarily" bug this is meant to prevent.
        long punctuationCount = timeline.stream().filter(w -> SENTENCE_END.matcher(w.text()).find()).count();
        boolean hasPunctuation = punctuationCount >= (long) Math.ceil(timeline.size() * PUNCTUATION_DENSITY_THRESHOLD);
        long pauseThresholdMs = hasPunctuation ? PAUSE_THRESHOLD_MS : NO_PUNCTUATION_PAUSE_THRESHOLD_MS;
        int maxWordsPerLine = hasPunctuation ? MAX_WORDS_PER_LINE : NO_PUNCTUATION_MAX_WORDS_PER_LINE;
        long maxLineDurationMs = hasPunctuation ? MAX_LINE_DURATION_MS : NO_PUNCTUATION_MAX_LINE_DURATION_MS;

        List<VttCue> result = new ArrayList<>();
        List<TimedWord> current = new ArrayList<>();
        long groupStart = timeline.get(0).timestampMs();

        for (TimedWord word : timeline) {
            if (!current.isEmpty()) {
                TimedWord last = current.get(current.size() - 1);
                boolean bigPause = word.timestampMs() - last.timestampMs() > pauseThresholdMs;
                boolean tooLong = current.size() >= maxWordsPerLine
                        || word.timestampMs() - groupStart > maxLineDurationMs;
                // No minimum chunk size here: an untagged token can be a whole sentence by
                // itself (e.g. a Japanese line with no per-word tags, or a short manual-
                // subtitle line), and that still deserves to end the line immediately.
                boolean priorSentenceEnded = SENTENCE_END.matcher(last.text()).find();
                if (bigPause || tooLong || priorSentenceEnded) {
                    result.add(finalizeCue(groupStart, current));
                    current = new ArrayList<>();
                    groupStart = word.timestampMs();
                }
            }
            current.add(word);
        }
        result.add(finalizeCue(groupStart, current));

        // Extend each line's end time to the next line's start so highlighting never has
        // a dead gap between lines during natural speech pauses.
        for (int i = 0; i < result.size() - 1; i++) {
            VttCue cue = result.get(i);
            result.set(i, new VttCue(cue.startMs(), result.get(i + 1).startMs(), cue.text()));
        }
        return result;
    }

    private static VttCue finalizeCue(long startMs, List<TimedWord> words) {
        long endMs = words.get(words.size() - 1).timestampMs() + LAST_LINE_TAIL_MS;
        return new VttCue(startMs, endMs, joinWords(words));
    }

    /**
     * Joins the per-word/per-character tokens back into text. CJK scripts (Japanese,
     * Chinese) don't put spaces between words at all - Japanese auto-captions tag each
     * individual character with its own timestamp, so naively space-joining every token
     * would scatter spaces between every single character (e.g. "は い 、 皆 さ ん"). Only
     * insert a space when neither side of the boundary is a CJK character.
     */
    private static String joinWords(List<TimedWord> words) {
        StringBuilder sb = new StringBuilder();
        String previous = null;
        for (TimedWord word : words) {
            String current = word.text();
            if (previous != null && needsSpaceBetween(previous, current)) {
                sb.append(' ');
            }
            sb.append(current);
            previous = current;
        }
        return sb.toString();
    }

    private static boolean needsSpaceBetween(String previous, String current) {
        if (previous.isEmpty() || current.isEmpty()) {
            return false;
        }
        char prevLast = previous.charAt(previous.length() - 1);
        char currFirst = current.charAt(0);
        return !isCjk(prevLast) && !isCjk(currFirst);
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    private static long toMillis(String hours, String minutes, String seconds, String millis) {
        long h = (hours == null) ? 0 : Long.parseLong(hours.replace(":", ""));
        long m = Long.parseLong(minutes);
        long s = Long.parseLong(seconds);
        long ms = Long.parseLong(millis);
        return h * 3_600_000L + m * 60_000L + s * 1000L + ms;
    }
}
