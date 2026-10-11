package echo.library.application;

/** The owning user does not exist (or has been erased). */
public class LibraryOwnerNotFoundException extends RuntimeException {

    public LibraryOwnerNotFoundException() {
        super("The library owner does not exist.");
    }
}
