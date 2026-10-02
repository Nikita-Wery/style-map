package ru.itis.stylemap.dto;

import java.math.BigDecimal;

public record LinkDto(
        String url,
        BigDecimal price,
        String currency
) {}