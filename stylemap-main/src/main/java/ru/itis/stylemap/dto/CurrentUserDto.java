package ru.itis.stylemap.dto;

import java.util.UUID;

/**
 * Информация о текущем аутентифицированном пользователе для SPA.
 */
public record CurrentUserDto(
        UUID id,
        String username,
        String email,
        String avatarUrl,
        String role
) {}
