package ru.itis.stylemap.dto;

import java.util.UUID;

public record FeedPostDto(
        UUID id,
        String mediaUrl,
        String mediaType
) {}