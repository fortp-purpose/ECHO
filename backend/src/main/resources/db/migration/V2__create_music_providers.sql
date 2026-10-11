-- Phase 5.1 - provider registry.
--
-- A provider is retired by changing its status, never by deleting the row, so
-- historical catalogue rows and any user data that references them survive a
-- provider withdrawal (domain-model 2.4). No provider may reach 'active' without
-- passing the four-point verification gate (music-provider-strategy 5.1).

CREATE TABLE music_providers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'unverified',
    capabilities    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_music_providers_code UNIQUE (code),
    CONSTRAINT chk_music_providers_status
        CHECK (status IN ('unverified', 'active', 'suspended', 'retired'))
);

CREATE INDEX idx_music_providers_status ON music_providers (status);
