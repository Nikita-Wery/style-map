package ru.itis.stylemap.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itis.stylemap.dto.PostUpdateDto;
import ru.itis.stylemap.service.CurrencyService;
import ru.itis.stylemap.service.PostService;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class PostActionApiController {

    private final CurrencyService currencyService;
    private final PostService postService;

    @GetMapping("/convert-currency")
    public ResponseEntity<?> convertCurrency(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        try {
            BigDecimal converted = currencyService.convert(amount, from, to);
            return ResponseEntity.ok(Map.of("status", "success", "result", converted, "currency", to));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/users/{authorId}/toggle-follow")
    public ResponseEntity<?> toggleFollow(@PathVariable UUID authorId, Principal principal) {
        return ResponseEntity.ok(postService.toggleFollow(authorId, principal));
    }

    @PostMapping("/posts/{postId}/rate")
    public ResponseEntity<?> ratePost(@PathVariable UUID postId, @RequestParam Short score, Principal principal) {
        if (score == null || score < 1 || score > 10) return ResponseEntity.badRequest().body("Неверная оценка");
        return ResponseEntity.ok(postService.ratePost(postId, principal, score));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable UUID postId, Principal principal) {
        return postService.deletePost(postId, principal);
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<?> updatePost(@PathVariable UUID postId,
                                        @RequestBody PostUpdateDto request,
                                        Principal principal) {
        return postService.updatePost(postId, request, principal);
    }
}