package ru.itis.stylemap.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.stylemap.controller.payload.NewUserPayload;
import ru.itis.stylemap.service.auth.AuthService;
import ru.itis.stylemap.service.UserAlreadyExistsException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final MessageSource messageSource;
    private static final Logger log = LoggerFactory.getLogger("USER_LOGGER");

    @GetMapping("/login")
    public String getLoginFormPage() {

        return "login";
    }

    @GetMapping("/register")
    public String getRegisterFormPage() {
        return "register";
    }

    @PostMapping("/register")
    public String registerByFormPage(@Valid NewUserPayload newUserPayload,
                                     BindingResult bindingResult,
                                     Model model,
                                     Locale locale,
                                     RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            List<String> messages = bindingResult.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .filter(Objects::nonNull)
                    .map(str -> messageSource.getMessage(str, new Object[0], str, locale))
                    .toList();
            log.atWarn()
                    .setMessage("ошибки валидации")
                    .addKeyValue("binding_errors.messages", messages)
                    .log();
            model.addAttribute("binding_errors", messages);
            return "register";
        }

        try {
            authService.registerUser(newUserPayload);
        } catch (UserAlreadyExistsException ex) {
            log.atError()
                    .setMessage("пользователь с такими данными уже существует")
                    .addKeyValue("error.message", ex.getMessage())
                    .log();
            model.addAttribute("errors", "пользователь с такими данными уже существует");
            return "register";
        }
        redirectAttributes.addFlashAttribute("email", newUserPayload.email());
        return "redirect:/auth/confirm";
    }

    @GetMapping("/confirm")
    public String getConfirmPage(Model model) {
        if (!model.containsAttribute("email")) {
            return "redirect:/auth/login";
        }
        return "confirm";
    }

    @PostMapping("/confirm")
    public String confirmCode(@RequestParam("email") String email,
                              @RequestParam("code") String code,
                              RedirectAttributes redirectAttributes) {
        boolean isConfirmed = authService.confirmUser(email, code);
        if (isConfirmed) {
            redirectAttributes.addFlashAttribute("message", "Аккаунт успешно подтвержден! Теперь вы можете войти.");
            log.atInfo()
                    .setMessage("Успешная регистрация")
                    .addKeyValue("email", email)
                    .addKeyValue("time", OffsetDateTime.now().toString())
                    .log();
            return "redirect:/auth/login";
        } else {
            log.atWarn()
                .setMessage("Неудачная попытка регистрации")
                    .addKeyValue("email", email)
                    .addKeyValue("time", OffsetDateTime.now().toString())
                    .log();
            redirectAttributes.addFlashAttribute("error", "Неверный или просроченный код.");
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/auth/confirm";
        }
    }


}