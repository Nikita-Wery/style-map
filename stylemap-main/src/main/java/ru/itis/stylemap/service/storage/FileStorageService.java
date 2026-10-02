package ru.itis.stylemap.service.storage;

import org.springframework.web.multipart.MultipartFile;
import ru.itis.stylemap.dto.FileInfo;
import ru.itis.stylemap.entity.PostMedia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface FileStorageService {
    FileInfo saveFile(MultipartFile file);

    void deleteFile(PostMedia postMedia);
}