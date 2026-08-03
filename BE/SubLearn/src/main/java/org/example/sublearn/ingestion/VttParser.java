package org.example.sublearn.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
     * break between sentences or thoughts - crosses it.
     */
    private static final long PAUSE_THRESHOLD_MS = 1800;
    /**
     * Caps so one long, pause-free monologue still breaks into readable pieces - a safety
     * net, not the primary mechanism, so it's set loose enough to rarely fire (it used to
     * be tight enough to cut mid-phrase, e.g. between "the" and "world").
     */
    private static final int MAX_WORDS_PER_LINE = 28;
    private static final long MAX_LINE_DURATION_MS = 14000;
    /** Manual/human subtitles carry punctuation auto-captions never do - a sentence-ending
     * mark is a much stronger signal of "complete idea" than any pause length, so it forces
     * a break right after it even if the next word follows almost immediately. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?][\"')\\]]?$");
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
        List<VttCue> result = new ArrayList<>();
        List<TimedWord> current = new ArrayList<>();
        long groupStart = timeline.get(0).timestampMs();

        for (TimedWord word : timeline) {
            if (!current.isEmpty()) {
                TimedWord last = current.get(current.size() - 1);
                boolean bigPause = word.timestampMs() - last.timestampMs() > PAUSE_THRESHOLD_MS;
                boolean tooLong = current.size() >= MAX_WORDS_PER_LINE
                        || word.timestampMs() - groupStart > MAX_LINE_DURATION_MS;
                boolean priorSentenceEnded = current.size() >= 2 && SENTENCE_END.matcher(last.text()).find();
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
        String text = words.stream().map(TimedWord::text).collect(Collectors.joining(" "));
        return new VttCue(startMs, endMs, text);
    }

    private static long toMillis(String hours, String minutes, String seconds, String millis) {
        long h = (hours == null) ? 0 : Long.parseLong(hours.replace(":", ""));
        long m = Long.parseLong(minutes);
        long s = Long.parseLong(seconds);
        long ms = Long.parseLong(millis);
        return h * 3_600_000L + m * 60_000L + s * 1000L + ms;
    }
}
