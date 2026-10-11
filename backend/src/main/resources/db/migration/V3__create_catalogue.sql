-- Phase 5.1 - catalogue mirror (artists, albums, tracks).
--
-- Every catalogue row is provider-namespaced from day one (architecture 8.2):
-- the natural key is (provider_id, provider_external_id). Rows are never
-- hard-deleted when a provider is withdrawn; the provider's status changes
-- instead, preserving references from user-owned data.

CREATE TABLE artists (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id           UUID         NOT NULL REFERENCES music_providers (id),
    provider_external_id  VARCHAR(255) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    normalised_name       VARCHAR(255) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_artists_provider_external UNIQUE (provider_id, provider_external_id)
);

CREATE INDEX idx_artists_provider ON artists (provider_id);
CREATE INDEX idx_artists_normalised_name ON artists (normalised_name);

CREATE TABLE albums (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id           UUID         NOT NULL REFERENCES music_providers (id),
    provider_external_id  VARCHAR(255) NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    primary_artist_id     UUID         REFERENCES artists (id),
    release_date          DATE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_albums_provider_external UNIQUE (provider_id, provider_external_id)
);

CREATE INDEX idx_albums_provider ON albums (provider_id);
CREATE INDEX idx_albums_primary_artist ON albums (primary_artist_id);
CREATE INDEX idx_albums_release_date ON albums (release_date);

CREATE TABLE tracks (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id           UUID         NOT NULL REFERENCES music_providers (id),
    provider_external_id  VARCHAR(255) NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    normalised_title      VARCHAR(255) NOT NULL,
    album_id              UUID         REFERENCES albums (id),
    duration_ms           INTEGER,
    is_available          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tracks_provider_external UNIQUE (provider_id, provider_external_id),
    CONSTRAINT chk_tracks_duration_ms CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX idx_tracks_provider ON tracks (provider_id);
CREATE INDEX idx_tracks_album ON tracks (album_id);
CREATE INDEX idx_tracks_normalised_title ON tracks (normalised_title);
