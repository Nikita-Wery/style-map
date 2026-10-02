package ru.itis.stylemap.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.stylemap.dto.FileInfo;
import ru.itis.stylemap.entity.MediaType;
import ru.itis.stylemap.entity.PostMedia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path fileStorageLocation;
    private final Logger log = LoggerFactory.getLogger("MAIN_LOGGER");

    private final String uploadDir;

    public LocalFileStorageService(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Не удалось создать директорию для загрузки файлов.", ex);
        }
    }

    @Override
    public FileInfo saveFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String newFileName = UUID.randomUUID() + fileExtension;
        String mimeType = file.getContentType();

        MediaType mediaType = (mimeType != null && mimeType.startsWith("video/")) ? MediaType.VIDEO : MediaType.IMAGE;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return new FileInfo("/uploads/" + newFileName, originalFileName, mimeType, mediaType);
        } catch (IOException ex) {
            throw new RuntimeException("Не удалось сохранить файл " + newFileName, ex);
        }
    }

    @Override
    public void deleteFile(PostMedia postMedia) {
        try {
            String storedPath = postMedia.getStoredFilePath();
            if (storedPath != null && storedPath.startsWith("/uploads/")) {
                storedPath = storedPath.replaceFirst("/uploads/", "");
            }
            Path path = Paths.get(uploadDir).resolve(storedPath).toAbsolutePath().normalize();

            boolean deleted = Files.deleteIfExists(path);
            if (!deleted) {
                log.atWarn()
                        .setMessage("Файл не найден на диске и не был удален")
                        .addKeyValue("path", path.toString())
                        .log();
            } else {
                log.info("Успешно удален файл с диска: {}", path);
            }
        } catch (IOException e) {
            log.error("Ошибка при удалении файла: {}", postMedia.getStoredFilePath(), e);
        }
    }
}