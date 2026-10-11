package echo.library.application;

/** A library request is malformed before it reaches the database. */
public class LibraryValidationException extends RuntimeException {

    public LibraryValidationException(String message) {
        super(message);
    }
}
