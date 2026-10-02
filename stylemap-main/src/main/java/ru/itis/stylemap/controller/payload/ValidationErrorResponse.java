package ru.itis.stylemap.controller.payload;

import java.util.List;

/**
 * Ошибки валидации формы, пришедшей из SPA.
 */
public record ValidationErrorResponse(String message, List<String> errors) {}
