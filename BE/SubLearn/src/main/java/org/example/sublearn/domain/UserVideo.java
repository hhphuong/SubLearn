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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_video", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_video_user_video", columnNames = {"user_id", "video_id"})
})
public class UserVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "last_position_ms", nullable = false)
    private long lastPositionMs;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "last_watched_at", nullable = false)
    private Instant lastWatchedAt;

    protected UserVideo() {
    }

    public UserVideo(AppUser user, Video video) {
        this.user = user;
        this.video = video;
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

    public long getLastPositionMs() {
        return lastPositionMs;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public Instant getLastWatchedAt() {
        return lastWatchedAt;
    }

    public void touch() {
        this.lastWatchedAt = Instant.now();
    }

    public void updateProgress(long positionMs) {
        this.lastPositionMs = Math.max(0, positionMs);
        touch();
    }
}
