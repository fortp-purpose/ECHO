package echo.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository extends JpaRepository<Album, UUID> {

    Optional<Album> findByProviderIdAndProviderExternalId(UUID providerId, String providerExternalId);

    List<Album> findByPrimaryArtistIdOrderByReleaseDateDesc(UUID primaryArtistId);
}
