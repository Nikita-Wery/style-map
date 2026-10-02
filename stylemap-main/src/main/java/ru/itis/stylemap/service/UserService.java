package ru.itis.stylemap.service;

import ru.itis.stylemap.dto.UserProfileDto;

public interface UserService {
    UserProfileDto getUserProfile(String targetUsername, String currentUsername);
}
