package ru.itis.stylemap.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import ru.itis.stylemap.controller.payload.ConfirmRequest;
import ru.itis.stylemap.controller.payload.ErrorResponse;
import ru.itis.stylemap.controller.payload.LoginRequest;
import ru.itis.stylemap.controller.payload.NewUserPayload;
import ru.itis.stylemap.controller.payload.ValidationErrorResponse;
import ru.itis.stylemap.dto.CurrentUserDto;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.repository.UserRepository;
import ru.itis.stylemap.service.UserAlreadyExistsException;
import ru.itis.stylemap.service.auth.AuthService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON-API аутентификации для React SPA.
 * Использует ту же сессионную аутентификацию Spring Security, что и Thymeleaf-страницы,
 * поэтому пользователь, вошедший через SPA, виден и в обычных страницах, и наоборот.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(Authentication authentication) {
        return resolveUser(authentication)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Unauthorized", "Пользователь не авторизован")));
    }

    /** Отдаёт актуальный CSRF-токен (и заодно гарантирует запись cookie XSRF-TOKEN). */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

            // Защита от session fixation: создаём сессию и меняем её идентификатор до сохранения контекста.
            httpRequest.getSession(true);
            httpRequest.changeSessionId();

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            return resolveUser(authentication)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.ok(new CurrentUserDto(
                            null, authentication.getName(), null, null, roleOf(authentication))));
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Unauthorized", "Неверное имя пользователя или пароль"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody NewUserPayload payload,
                                      BindingResult bindingResult,
                                      Locale locale) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                    "Ошибка валидации", localizedErrors(bindingResult, locale)));
        }
        try {
            authService.registerUser(payload);
        } catch (UserAlreadyExistsException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Conflict", "пользователь с такими данными уже существует"));
        }
        return ResponseEntity.ok(Map.of("status", "success", "email", payload.email()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@Valid @RequestBody ConfirmRequest request) {
        boolean confirmed = authService.confirmUser(request.email(), request.code());
        if (!confirmed) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Bad Request", "Неверный или просроченный код."));
        }
        return ResponseEntity.ok(Map.of("status", "success", "message", "Аккаунт успешно подтвержден!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    private Optional<CurrentUserDto> resolveUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByUsername(authentication.getName()).map(this::toDto);
    }

    private CurrentUserDto toDto(User user) {
        return new CurrentUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole().name()
        );
    }

    private String roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("USER");
    }

    private List<String> localizedErrors(BindingResult bindingResult, Locale locale) {
        return bindingResult.getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(Objects::nonNull)
                .map(message -> messageSource.getMessage(message, new Object[0], message, locale))
                .toList();
    }
}
