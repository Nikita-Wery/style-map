package ru.itis.stylemap.controller.payload;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "имя пользователя обязательно") String username,
        @NotBlank(message = "пароль обязателен") String password
) {}
