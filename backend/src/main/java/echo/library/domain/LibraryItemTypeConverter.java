package echo.library.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link LibraryItemType} using its stable lower-case code so the Java
 * enum and the database CHECK constraint stay in agreement.
 */
@Converter
public class LibraryItemTypeConverter implements AttributeConverter<LibraryItemType, String> {

    @Override
    public String convertToDatabaseColumn(LibraryItemType attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public LibraryItemType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LibraryItemType.fromCode(dbData);
    }
}
