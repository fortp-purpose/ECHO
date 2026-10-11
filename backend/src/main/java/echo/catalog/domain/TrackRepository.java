package echo.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<Track, UUID> {

    Optional<Track> findByProviderIdAndProviderExternalId(UUID providerId, String providerExternalId);

    List<Track> findByAlbumIdOrderByTitleAsc(UUID albumId);
}
