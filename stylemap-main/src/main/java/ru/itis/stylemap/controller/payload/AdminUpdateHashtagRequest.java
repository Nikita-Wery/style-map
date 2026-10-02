package ru.itis.stylemap.controller.payload;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdateHashtagRequest(
        @NotBlank(message = "имя обязательно") String newName
) {}
