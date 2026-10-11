package echo.identity.application;

import java.util.UUID;

/**
 * Published, read-only view of the identity module for other modules
 * (AGENTS.md 5: cross-module access goes through application interfaces, never
 * repositories or entities). Authentication is a later phase; this exposes only
 * existence.
 */
public interface UserDirectory {

    /** True when a non-deleted user with this id exists. */
    boolean exists(UUID userId);
}
