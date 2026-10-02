package ru.itis.stylemap.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Для запросов к REST API отдаёт 401 в формате JSON вместо редиректа на HTML-страницу логина,
 * чтобы SPA мог самостоятельно обработать неавторизованный доступ.
 * Для обычных (Thymeleaf) запросов поведение остаётся прежним.
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint htmlEntryPoint;

    public JsonAuthenticationEntryPoint(AuthenticationEntryPoint htmlEntryPoint) {
        this.htmlEntryPoint = htmlEntryPoint;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (isApiRequest(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Unauthorized\",\"details\":\"Требуется вход в систему.\"}");
            return;
        }
        this.htmlEntryPoint.commence(request, response, authException);
    }

    static boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/");
    }
}
