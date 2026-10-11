-- Phase 5.2 - personal library (unified LibraryItem, ADR-018).
--
-- A user keeps a track or an album, either as a "saved" item or a "favorite".
-- The two ECHO.pdf-named capabilities share one table via a `type`
-- discriminator rather than two near-identical tables. Exactly one subject
-- (track or album) is set. The two PDF signal types remain distinct in the
-- domain: `favorite` -> LIKING, `saved` -> SAVING.
--
-- user_id cascades on delete (privacy erasure, security-strategy 15).
-- track_id/album_id do not cascade: catalogue rows are withdrawn by status
-- change, never hard-deleted.

CREATE TABLE library_items (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id    UUID        REFERENCES tracks (id),
    album_id    UUID        REFERENCES albums (id),
    type        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_library_items_type CHECK (type IN ('saved', 'favorite')),
    CONSTRAINT chk_library_items_subject CHECK (
        (track_id IS NOT NULL AND album_id IS NULL) OR
        (track_id IS NULL AND album_id IS NOT NULL)
    ),
    CONSTRAINT uq_library_items_user_track_type UNIQUE (user_id, track_id, type),
    CONSTRAINT uq_library_items_user_album_type UNIQUE (user_id, album_id, type)
);

CREATE INDEX idx_library_items_user ON library_items (user_id);
CREATE INDEX idx_library_items_track ON library_items (track_id);
CREATE INDEX idx_library_items_album ON library_items (album_id);
CREATE INDEX idx_library_items_user_type ON library_items (user_id, type);
