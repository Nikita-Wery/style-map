package ru.itis.stylemap.config;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        // REST API отвечает JSON-ом, SPA сам решает, что показать пользователю.
        if (JsonAuthenticationEntryPoint.isApiRequest(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Forbidden\",\"details\":\"У вас нет прав на это действие.\"}");
            return;
        }

        if (request.getUserPrincipal() != null) {
            response.sendRedirect("/showroom/home");
        } else {
            response.sendRedirect("/auth/login");
        }
    }
}
