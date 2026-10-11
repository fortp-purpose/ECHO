package echo.catalog.application;

/**
 * Raised when the catalogue cannot be served because no verified provider is
 * active. This is the documented CATALOG_PROVIDER_UNAVAILABLE steady state
 * (architecture 12.3), not a defect.
 */
public class CatalogProviderUnavailableException extends RuntimeException {

    public CatalogProviderUnavailableException() {
        super("No verified music provider is active.");
    }
}
