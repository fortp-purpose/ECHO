package echo.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArtistRepository extends JpaRepository<Artist, UUID> {

    Optional<Artist> findByProviderIdAndProviderExternalId(UUID providerId, String providerExternalId);
}
