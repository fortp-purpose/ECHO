package echo.catalog.application;

/**
 * Raised when a catalogue resource does not exist while a provider is active.
 */
public class CatalogResourceNotFoundException extends RuntimeException {

    public CatalogResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
