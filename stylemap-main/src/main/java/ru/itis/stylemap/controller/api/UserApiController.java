package ru.itis.stylemap.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itis.stylemap.dto.UserProfileDto;
import ru.itis.stylemap.service.UserService;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    @GetMapping("/{username}")
    public ResponseEntity<UserProfileDto> getProfile(@PathVariable String username, Principal principal) {
        String currentUsername = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(userService.getUserProfile(username, currentUsername));
    }
}
