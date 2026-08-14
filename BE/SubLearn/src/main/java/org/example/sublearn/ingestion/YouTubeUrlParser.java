package org.example.sublearn.ingestion;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class YouTubeUrlParser {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{11}");

    public Optional<String> extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (host == null) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        String candidate = null;
        if (host.endsWith("youtu.be")) {
            if (path.length() > 1) {
                candidate = path.substring(1);
            }
        } else if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            if (path.startsWith("/shorts/") || path.startsWith("/embed/") || path.startsWith("/live/")) {
                candidate = path.substring(path.lastIndexOf('/') + 1);
            } else {
                candidate = queryParam(uri.getQuery(), "v");
            }
        } else {
            return Optional.empty();
        }

        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        candidate = candidate.split("[?&/]")[0];
        return VIDEO_ID_PATTERN.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }

    private String queryParam(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }
}
