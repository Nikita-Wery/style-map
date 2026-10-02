package ru.itis.stylemap.dto;

import java.util.UUID;

public record AdminUserDto(
        UUID id,
        String username,
        String email,
        String role,
        boolean active
) {}
