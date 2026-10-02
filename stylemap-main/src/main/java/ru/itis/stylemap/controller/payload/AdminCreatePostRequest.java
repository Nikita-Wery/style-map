package ru.itis.stylemap.controller.payload;

import jakarta.validation.constraints.NotBlank;

public record AdminCreatePostRequest(
        @NotBlank(message = "почта автора обязательна") String authorEmail,
        @NotBlank(message = "описание обязательно") String description
) {}
