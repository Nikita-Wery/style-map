package ru.itis.stylemap.controller.payload;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NewUserPayload(
        @NotNull(message = "имя обязательно")
        @NotBlank(message = "имя не может быть пустым")
        @Size(min = 4, max = 100, message = "размер имени от 4 до 100 символов")
        String username,

        @NotNull(message = "почта обязательна")
        @Email(message = "не соответствует формату почты")
        String email,

        @NotNull(message = "пароль обязателен")
        @Size(min = 4, max = 50, message = "размер пароля должен быть от 4 до 50 символов")
        String password
) {
}
