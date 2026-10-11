package echo.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link ProviderStatus} using its stable lower-case code so the Java
 * enum and the database CHECK constraint stay in agreement.
 */
@Converter
public class ProviderStatusConverter implements AttributeConverter<ProviderStatus, String> {

    @Override
    public String convertToDatabaseColumn(ProviderStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ProviderStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProviderStatus.fromCode(dbData);
    }
}
