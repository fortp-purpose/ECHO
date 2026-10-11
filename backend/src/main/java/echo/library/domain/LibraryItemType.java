package echo.library.domain;

/**
 * The two ECHO.pdf-named ways a user keeps an item (ADR-018). They share one
 * table but remain distinct as AI signals: a favourite emits LIKING, a saved
 * item emits SAVING (ai-strategy 5).
 */
public enum LibraryItemType {

    SAVED("saved", "SAVING"),
    FAVORITE("favorite", "LIKING");

    private final String code;
    private final String preferenceSignal;

    LibraryItemType(String code, String preferenceSignal) {
        this.code = code;
        this.preferenceSignal = preferenceSignal;
    }

    public String code() {
        return code;
    }

    /** The PreferenceSignal type this library item contributes to the AI layer. */
    public String preferenceSignal() {
        return preferenceSignal;
    }

    public static LibraryItemType fromCode(String code) {
        for (LibraryItemType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown library item type: " + code);
    }
}
