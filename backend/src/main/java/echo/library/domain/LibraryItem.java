package echo.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A track or album a user has kept (ADR-018). One table holds both `saved` and
 * `favorite` items. Exactly one subject ({@code trackId} or {@code albumId}) is
 * set; the database enforces this with {@code chk_library_items_subject}.
 *
 * <p>The subject is stored as a raw identifier rather than a JPA association so
 * the library module does not reach into the catalogue module's entities. The
 * database foreign keys still restore referential integrity, and existence is
 * validated through the catalogue's published application interface.
 */
@Entity
@Table(name = "library_items")
public class LibraryItem {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "UUID", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "track_id", columnDefinition = "UUID", updatable = false)
    private UUID trackId;

    @Column(name = "album_id", columnDefinition = "UUID", updatable = false)
    private UUID albumId;

    @Convert(converter = LibraryItemTypeConverter.class)
    @Column(name = "type", nullable = false, updatable = false)
    private LibraryItemType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LibraryItem() {
        this.id = UUID.randomUUID();
    }

    private LibraryItem(UUID userId, UUID trackId, UUID albumId, LibraryItemType type) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.trackId = trackId;
        this.albumId = albumId;
        this.type = type;
    }

    public static LibraryItem track(UUID userId, UUID trackId, LibraryItemType type) {
        if (trackId == null) {
            throw new IllegalArgumentException("trackId is required for a track library item");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        return new LibraryItem(userId, trackId, null, type);
    }

    public static LibraryItem album(UUID userId, UUID albumId, LibraryItemType type) {
        if (albumId == null) {
            throw new IllegalArgumentException("albumId is required for an album library item");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        return new LibraryItem(userId, null, albumId, type);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public UUID getAlbumId() {
        return albumId;
    }

    public LibraryItemType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onPersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
