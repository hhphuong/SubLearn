package org.example.sublearn.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal WebVTT cue parser: extracts (startMs, endMs, text) triples, strips inline
 * markup tags (e.g. YouTube's karaoke-style {@code <00:00:01.234><c>word</c>} timing
 * tags), and skips consecutive cues with identical text — YouTube auto-generated
 * captions frequently "roll" the same line across multiple cues as more words arrive.
 */
public final class VttParser {

    private static final Pattern TIMESTAMP_LINE = Pattern.compile(
            "(\\d{2}:)?(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(\\d{2}:)?(\\d{2}):(\\d{2})\\.(\\d{3})");
    private static final Pattern INLINE_TAG = Pattern.compile("<[^>]*>");

    private VttParser() {
    }

    public static List<VttCue> parse(String vttContent) {
        if (vttContent == null || vttContent.isBlank()) {
            return List.of();
        }
        String[] lines = vttContent.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        List<VttCue> cues = new ArrayList<>();
        String previousText = null;
        int i = 0;
        while (i < lines.length) {
            Matcher matcher = TIMESTAMP_LINE.matcher(lines[i]);
            if (matcher.find()) {
                long startMs = toMillis(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
                long endMs = toMillis(matcher.group(5), matcher.group(6), matcher.group(7), matcher.group(8));
                i++;
                StringBuilder textBuilder = new StringBuilder();
                while (i < lines.length && !lines[i].isBlank()) {
                    String textLine = INLINE_TAG.matcher(lines[i]).replaceAll("").trim();
                    if (!textLine.isEmpty()) {
                        if (!textBuilder.isEmpty()) {
                            textBuilder.append(' ');
                        }
                        textBuilder.append(textLine);
                    }
                    i++;
                }
                String text = textBuilder.toString().trim();
                if (!text.isEmpty() && !text.equals(previousText)) {
                    cues.add(new VttCue(startMs, endMs, text));
                    previousText = text;
                }
            } else {
                i++;
            }
        }
        return cues;
    }

    private static long toMillis(String hours, String minutes, String seconds, String millis) {
        long h = (hours == null) ? 0 : Long.parseLong(hours.replace(":", ""));
        long m = Long.parseLong(minutes);
        long s = Long.parseLong(seconds);
        long ms = Long.parseLong(millis);
        return h * 3_600_000L + m * 60_000L + s * 1000L + ms;
    }
}
