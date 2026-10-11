package echo.catalog.domain;

import java.util.Set;

/**
 * Capabilities a provider declares it supports. Kept as structured data so the
 * application can degrade honestly instead of assuming every provider supports
 * every operation (music-provider-strategy 2.3).
 */
public record ProviderCapabilities(
        boolean search,
        boolean artistMetadata,
        boolean albumMetadata,
        boolean streaming,
        boolean providerRecommendations,
        boolean artwork,
        boolean lyrics,
        int maxResultsPerPage,
        boolean paginationCursors,
        Set<String> supportedFilters
) {

    public static ProviderCapabilities none() {
        return new ProviderCapabilities(
                false, false, false, false, false, false, false, 0, false, Set.of());
    }
}
