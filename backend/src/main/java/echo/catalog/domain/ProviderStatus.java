package echo.catalog.domain;

/**
 * Lifecycle of a registered music provider. A provider never reaches
 * {@link #ACTIVE} without passing the four-point verification gate
 * (music-provider-strategy 5.1), and it is retired by status change rather than
 * row deletion so historical catalogue data survives.
 */
public enum ProviderStatus {

    UNVERIFIED("unverified"),
    ACTIVE("active"),
    SUSPENDED("suspended"),
    RETIRED("retired");

    private final String code;

    ProviderStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ProviderStatus fromCode(String code) {
        for (ProviderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown provider status: " + code);
    }
}
