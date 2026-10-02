package ru.itis.stylemap.controller.payload;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdatePostRequest(
        @NotBlank(message = "описание обязательно") String newDescription
) {}
