package echo.library.application;

/** The user already keeps this subject with this type. */
public class LibraryDuplicateItemException extends RuntimeException {

    public LibraryDuplicateItemException() {
        super("The item is already in the library.");
    }
}
