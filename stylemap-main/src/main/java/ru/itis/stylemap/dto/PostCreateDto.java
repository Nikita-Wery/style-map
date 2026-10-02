package ru.itis.stylemap.dto;

import java.util.List;

public record PostCreateDto(
        String description,
        List<String> hashtags,
        List<OutfitItemDto> outfitItems
) {}