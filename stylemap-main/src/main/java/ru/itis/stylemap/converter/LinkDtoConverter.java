package ru.itis.stylemap.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import ru.itis.stylemap.dto.LinkDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import java.util.HashMap;
import java.util.Map;

@Converter
@RequiredArgsConstructor
public class LinkDtoConverter implements AttributeConverter<Map<String, LinkDto>, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(Map<String, LinkDto> attribute) {
        try {
            if (attribute == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Ошибка сериализации JSON", e);
        }
    }

    @Override
    public Map<String, LinkDto> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(dbData, new TypeReference<Map<String, LinkDto>>() {});
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Ошибка десериализации JSON", e);
        }
    }
}