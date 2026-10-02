package ru.itis.stylemap.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.itis.stylemap.controller.payload.NewUserPayload;
import ru.itis.stylemap.service.AdminService;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final MessageSource messageSource;

    @GetMapping
    public String adminDashboard() {
        return "/admin/dashboard";
    }

    @PostMapping("/users/create")
    public String createUser(@Valid NewUserPayload newUserPayload, Model model, BindingResult bindingResult, Locale locale) {
        adminService.createUser(newUserPayload);
        model.addAttribute("errors",
                bindingResult.getAllErrors().stream()
                        .map(ObjectError::getDefaultMessage)
                        .filter(Objects::nonNull)
                        .map(str -> messageSource.getMessage(str, new Object[0], str, locale))
                        .toList());
        return "redirect:/admin";
    }

    @PostMapping("/users/update")
    public String banUser(UUID userId, boolean isActive) {
        adminService.banUser(userId, isActive);
        return "redirect:/admin";
    }

    @PostMapping("/posts/create")
    public String createPost(String authorEmail, String description) {
        adminService.createPost(authorEmail, description);
        return "redirect:/admin";
    }

    @PostMapping("/posts/update")
    public String updatePost(UUID postId, String newDescription) {
        adminService.updatePost(postId, newDescription);
        return "redirect:/admin";
    }

    @PostMapping("/hashtags/create")
    public String createHashtag(String name, String path) {
        adminService.createHashtag(name, path);
        return "redirect:/admin";
    }

    @PostMapping("/hashtags/update")
    public String updateHashtag(Long hashtagId, String newName){
        adminService.updateHashtag(hashtagId, newName);
        return "redirect:/admin";
    }

    @PostMapping("/users/delete")
    public String deleteUser(UUID userId){
        adminService.deleteUser(userId);
        return "redirect:/admin";
    }


    @PostMapping("/hashtags/delete")
    public String deleteUser(Long hashtagId){
        adminService.deleteHashtag(hashtagId);
        return "redirect:/admin";
    }

    @PostMapping("/posts/delete")
    public String deletePost(UUID postId){
        adminService.deletePost(postId);
        return "redirect:/admin";
    }

}
