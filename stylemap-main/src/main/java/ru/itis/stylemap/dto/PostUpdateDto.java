package ru.itis.stylemap.dto;

import java.util.List;

public record PostUpdateDto(
        String description,
        List<String> hashtags,
        List<OutfitItemDto> outfitItems
) {}