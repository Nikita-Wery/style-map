package ru.itis.stylemap.dto;

import java.util.Map;

public record OutfitItemDto(
        String name,
        String brand,
        Map<String, LinkDto> links
) {}