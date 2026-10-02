package ru.itis.stylemap.dto;

import ru.itis.stylemap.entity.MediaType;

public record FileInfo(
        String storedFilePath,
        String originalFileName,
        String mimeType,
        MediaType mediaType
) {}