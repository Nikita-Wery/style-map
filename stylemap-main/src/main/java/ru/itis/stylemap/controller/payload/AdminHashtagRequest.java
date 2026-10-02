package ru.itis.stylemap.controller.payload;

import jakarta.validation.constraints.NotBlank;

public record AdminHashtagRequest(
        @NotBlank(message = "имя обязательно") String name,
        @NotBlank(message = "путь обязателен") String path
) {}
