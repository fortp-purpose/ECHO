package echo.library.application;

import echo.catalog.application.CatalogService;
import echo.identity.application.UserDirectory;
import echo.library.domain.LibraryItem;
import echo.library.domain.LibraryItemRepository;
import echo.library.domain.LibraryItemType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owner-scoped personal library (ADR-018). All operations take the acting
 * user's id explicitly; the identity of the caller is supplied by the
 * authentication layer and is never asserted by the client. Cross-module
 * validation goes through published application interfaces: the catalogue
 * (does the track/album exist and is it servable) and identity (does the user
 * exist).
 *
 * <p>Both "saved" and "favorite" items share this service and one table. The
 * distinction survives as the {@link LibraryItemType} on each row, which maps
 * to a distinct AI signal (SAVING vs LIKING).
 */
@Service
public class LibraryService {

    private final LibraryItemRepository items;
    private final UserDirectory users;
    private final CatalogService catalogue;

    public LibraryService(LibraryItemRepository items, UserDirectory users, CatalogService catalogue) {
        this.items = items;
        this.users = users;
        this.catalogue = catalogue;
    }

    @Transactional
    public LibraryItemView addTrack(UUID userId, UUID trackId, LibraryItemType type) {
        requireUser(userId);
        catalogue.getTrack(trackId);
        if (items.existsByUserIdAndTrackIdAndType(userId, trackId, type)) {
            throw new LibraryDuplicateItemException();
        }
        return toView(items.save(LibraryItem.track(userId, trackId, type)));
    }

    @Transactional
    public LibraryItemView addAlbum(UUID userId, UUID albumId, LibraryItemType type) {
        requireUser(userId);
        catalogue.getAlbum(albumId);
        if (items.existsByUserIdAndAlbumIdAndType(userId, albumId, type)) {
            throw new LibraryDuplicateItemException();
        }
        return toView(items.save(LibraryItem.album(userId, albumId, type)));
    }

    @Transactional
    public void remove(UUID userId, UUID itemId) {
        LibraryItem item = items.findByIdAndUserId(itemId, userId)
                .orElseThrow(LibraryItemNotFoundException::new);
        items.delete(item);
    }

    @Transactional(readOnly = true)
    public List<LibraryItemView> list(UUID userId, LibraryItemType type) {
        List<LibraryItem> found = (type == null)
                ? items.findByUserIdOrderByCreatedAtDesc(userId)
                : items.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
        return found.stream().map(LibraryService::toView).toList();
    }

    private void requireUser(UUID userId) {
        if (!users.exists(userId)) {
            throw new LibraryOwnerNotFoundException();
        }
    }

    private static LibraryItemView toView(LibraryItem item) {
        return new LibraryItemView(
                item.getId(),
                item.getTrackId(),
                item.getAlbumId(),
                item.getType().code(),
                item.getCreatedAt());
    }
}
