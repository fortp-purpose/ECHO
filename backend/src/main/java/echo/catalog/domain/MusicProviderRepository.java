package echo.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MusicProviderRepository extends JpaRepository<MusicProvider, UUID> {

    boolean existsByStatus(ProviderStatus status);

    Optional<MusicProvider> findByCode(String code);
}
