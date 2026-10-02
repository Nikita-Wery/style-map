package ru.itis.stylemap.controller.advice;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.itis.stylemap.controller.payload.ErrorResponse;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger("MAIN_LOGGER");

    private boolean isAjaxRequest(HttpServletRequest request) {
        // Запросы к REST API всегда получают JSON-ответ, независимо от заголовка Accept.
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        String acceptHeader = request.getHeader("Accept");
        return (acceptHeader != null && acceptHeader.contains("application/json"));
    }

    @ExceptionHandler({NoSuchElementException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request, Model model) {
        log.atError()
                .setMessage("Ресурс не найден")
                .addKeyValue("error.message", ex.getMessage())
                .setCause(ex)
                .log();
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Not Found", "Запрашиваемый ресурс отсутствует."));
        }
        model.addAttribute("error", "Запрашиваемый ресурс отсутствует.");
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Model model) {
        log.atError()
                .setMessage("Доступ запрещен")
                .addKeyValue("error.message", ex.getMessage())
                .setCause(ex)
                .log();
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Forbidden", "У вас нет прав на это действие."));
        }
        model.addAttribute("error", "У вас нет прав на это действие.");
        return "error/403";
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public Object handleBadRequest(Exception ex, HttpServletRequest request, Model model) {
        log.atError()
                .setMessage("некоректный запрос")
                .addKeyValue("error.message", ex.getMessage())
                .setCause(ex)
                .log();
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Bad Request", "Переданы некорректные данные."));
        }
        model.addAttribute("error", "Переданы некорректные данные.");
        return "error/400";
    }

    @ExceptionHandler(org.apache.catalina.connector.ClientAbortException.class)
    public void handleLockup() {
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneralException(Exception ex, HttpServletRequest request, Model model) {
        log.atError()
                .setMessage("ошибка со стороны сервера")
                .addKeyValue("error.message", ex.getMessage())
                .setCause(ex)
                .log();
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Internal Server Error", "Произошла внутренняя ошибка."));
        }
        model.addAttribute("error", "Что-то пошло не так. Мы уже чиним!");
        return "error/500";
    }
}