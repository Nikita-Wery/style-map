package ru.itis.stylemap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminPostDto(
        UUID id,
        String description,
        String authorUsername,
        BigDecimal averageRating,
        Instant createdAt
) {}
