package echo.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracks")
public class Track {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private MusicProvider provider;

    @Column(name = "provider_external_id", nullable = false)
    private String providerExternalId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "normalised_title", nullable = false)
    private String normalisedTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Track() {
        this.id = UUID.randomUUID();
    }

    public Track(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public MusicProvider getProvider() {
        return provider;
    }

    public void setProvider(MusicProvider provider) {
        this.provider = provider;
    }

    public String getProviderExternalId() {
        return providerExternalId;
    }

    public void setProviderExternalId(String providerExternalId) {
        this.providerExternalId = providerExternalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNormalisedTitle() {
        return normalisedTitle;
    }

    public void setNormalisedTitle(String normalisedTitle) {
        this.normalisedTitle = normalisedTitle;
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    protected void onPersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
