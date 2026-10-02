package ru.itis.stylemap.controller.payload;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(
        @NotBlank(message = "почта обязательна") String email,
        @NotBlank(message = "код обязателен") String code
) {}
