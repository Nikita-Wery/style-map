package ru.itis.stylemap.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.itis.stylemap.dto.PostDetailsDto;
import ru.itis.stylemap.dto.UserProfileDto;
import ru.itis.stylemap.service.PostService;
import ru.itis.stylemap.service.ShowroomService;
import ru.itis.stylemap.service.UserService;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequestMapping("/showroom")
@RequiredArgsConstructor
public class ShowroomController {

    private final ShowroomService showroomService;
    private final PostService postService;
    private final UserService userService;

    @GetMapping("/home")
    public String homePage(@RequestParam(value = "request", required = false) String request, Model model) {
        model.addAttribute("posts", showroomService.getPosts(request));
        model.addAttribute("request", request);
        model.addAttribute("message", "Добро пожаловать в StyleMap");
        return "showroom/home/homePage";
    }

    @GetMapping("/createPost")
    public String createPostAction() {
        return "showroom/home/createPost";
    }

    @GetMapping("/post/{id}")
    public String viewSinglePost(@PathVariable UUID id, Model model, Principal principal) {
        String username = principal != null ? principal.getName() : null;
        PostDetailsDto postDetails = postService.getPostDetails(id, username);
        model.addAttribute("post", postDetails);
        return "showroom/home/postDetails";
    }

    @GetMapping("/user/{username}")
    public String viewProfile(@PathVariable String username, Model model, Principal principal) {
        String currentUsername = principal != null ? principal.getName() : null;

        UserProfileDto profileInfo = userService.getUserProfile(username, currentUsername);
        model.addAttribute("profile", profileInfo);
        return "showroom/profile";
    }

    @GetMapping("/profile")
    public String viewMyProfile(Principal principal) {
        return "redirect:/showroom/user/" + principal.getName();
    }
}