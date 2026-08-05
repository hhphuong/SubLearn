package org.example.sublearn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tracks the most recent time each user watched each video, independent of who originally
 * submitted it - videos are shared/cached across users by {@code youtube_video_id}, so
 * {@link Video#getRequestedByUser()} only reflects the first submitter, not everyone who has
 * since watched it. One row per (user, video); {@link #touch()} bumps it on every visit.
 */
@Entity
@Table(name = "video_watch_history")
public class VideoWatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "last_watched_at", nullable = false)
    private Instant lastWatchedAt;

    protected VideoWatchHistory() {
    }

    public VideoWatchHistory(AppUser user, Video video) {
        this.user = user;
        this.video = video;
        this.lastWatchedAt = Instant.now();
    }

    public void touch() {
        this.lastWatchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Video getVideo() {
        return video;
    }

    public Instant getLastWatchedAt() {
        return lastWatchedAt;
    }
}
