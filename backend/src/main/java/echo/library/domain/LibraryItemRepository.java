package echo.library.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, UUID> {

    List<LibraryItem> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<LibraryItem> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, LibraryItemType type);

    Optional<LibraryItem> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTrackIdAndType(UUID userId, UUID trackId, LibraryItemType type);

    boolean existsByUserIdAndAlbumIdAndType(UUID userId, UUID albumId, LibraryItemType type);
}
