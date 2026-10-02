package ru.itis.stylemap.controller.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import ru.itis.stylemap.controller.payload.*;
import ru.itis.stylemap.dto.AdminHashtagDto;
import ru.itis.stylemap.dto.AdminPostDto;
import ru.itis.stylemap.dto.AdminUserDto;
import ru.itis.stylemap.entity.Hashtag;
import ru.itis.stylemap.entity.Post;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.service.AdminService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JSON-API админ-панели для SPA. Доступ ограничен ролью ADMIN в SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final AdminService adminService;
    private final MessageSource messageSource;

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> getUsers() {
        return ResponseEntity.ok(adminService.getAllUsers().stream().map(this::toDto).toList());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody NewUserPayload payload,
                                        BindingResult bindingResult,
                                        Locale locale) {
        if (bindingResult.hasErrors()) {
            return badRequest(bindingResult, locale);
        }
        adminService.createUser(payload);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable UUID userId,
                                              @RequestBody AdminUserStatusRequest request) {
        adminService.banUser(userId, request.active());
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    // ------------------------------------------------------------------ posts

    @GetMapping("/posts")
    public ResponseEntity<List<AdminPostDto>> getPosts() {
        return ResponseEntity.ok(adminService.getAllPosts().stream().map(this::toDto).toList());
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@Valid @RequestBody AdminCreatePostRequest request,
                                        BindingResult bindingResult,
                                        Locale locale) {
        if (bindingResult.hasErrors()) {
            return badRequest(bindingResult, locale);
        }
        adminService.createPost(request.authorEmail(), request.description());
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<?> updatePost(@PathVariable UUID postId,
                                        @Valid @RequestBody AdminUpdatePostRequest request) {
        adminService.updatePost(postId, request.newDescription());
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable UUID postId) {
        adminService.deletePost(postId);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    // --------------------------------------------------------------- hashtags

    @GetMapping("/hashtags")
    public ResponseEntity<List<AdminHashtagDto>> getHashtags() {
        return ResponseEntity.ok(adminService.getAllHashtags().stream().map(this::toDto).toList());
    }

    @PostMapping("/hashtags")
    public ResponseEntity<?> createHashtag(@Valid @RequestBody AdminHashtagRequest request,
                                           BindingResult bindingResult,
                                           Locale locale) {
        if (bindingResult.hasErrors()) {
            return badRequest(bindingResult, locale);
        }
        adminService.createHashtag(request.name(), request.path());
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PutMapping("/hashtags/{hashtagId}")
    public ResponseEntity<?> updateHashtag(@PathVariable Long hashtagId,
                                           @Valid @RequestBody AdminUpdateHashtagRequest request) {
        adminService.updateHashtag(hashtagId, request.newName());
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @DeleteMapping("/hashtags/{hashtagId}")
    public ResponseEntity<?> deleteHashtag(@PathVariable Long hashtagId) {
        adminService.deleteHashtag(hashtagId);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    // ----------------------------------------------------------------- helpers

    private ResponseEntity<ValidationErrorResponse> badRequest(BindingResult bindingResult, Locale locale) {
        List<String> errors = bindingResult.getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(Objects::nonNull)
                .map(message -> messageSource.getMessage(message, new Object[0], message, locale))
                .toList();
        return ResponseEntity.badRequest().body(new ValidationErrorResponse("Ошибка валидации", errors));
    }

    private AdminUserDto toDto(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive()
        );
    }

    private AdminPostDto toDto(Post post) {
        return new AdminPostDto(
                post.getId(),
                post.getDescription(),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getAverageRating(),
                post.getCreatedAt()
        );
    }

    private AdminHashtagDto toDto(Hashtag hashtag) {
        return new AdminHashtagDto(hashtag.getId(), hashtag.getName(), hashtag.getPath());
    }
}
