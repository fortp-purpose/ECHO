package echo.library.application;

/**
 * The addressed library item does not exist, or is not owned by the caller.
 * Both cases are deliberately indistinguishable so an invisible resource is a
 * {@code 404}, never a {@code 403} (security-strategy 4/6).
 */
public class LibraryItemNotFoundException extends RuntimeException {

    public LibraryItemNotFoundException() {
        super("The requested library item was not found.");
    }
}
