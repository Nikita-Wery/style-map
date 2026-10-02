package ru.itis.stylemap.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.stylemap.dto.FeedPostDto;
import ru.itis.stylemap.dto.PostDetailsDto;
import ru.itis.stylemap.service.PostService;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostAPIController {

    private final PostService postService;


    @PostMapping
    public ResponseEntity<?> createPost(
            @RequestPart("file") MultipartFile file,
            @RequestPart("postData") String postDataJson,
            Principal principal) {

        postService.createPostWithDetails(principal.getName(), file, postDataJson);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Аутфит успешно опубликован!"));
    }

    @GetMapping("/feed")
    public ResponseEntity<Page<FeedPostDto>> getFeed(
            @RequestParam(value = "hashtag", required = false) String hashtag,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "date_desc") String sortBy,
            @RequestParam(value = "followingOnly", defaultValue = "false") boolean followingOnly,
            Principal principal) {

        return ResponseEntity.ok(postService.getFeed(hashtag, page, size, sortBy, followingOnly, principal));

    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<FeedPostDto>> getUserPosts(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {


        Page<FeedPostDto> dtoPage = postService.getUserPosts(userId, page, size);
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDetailsDto> getPostDetails(@PathVariable UUID id, Principal principal) {
        String username = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(postService.getPostDetails(id, username));
    }
}