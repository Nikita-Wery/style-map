package ru.itis.stylemap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.stylemap.dto.UserProfileDto;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.repository.UserFollowsRepository;
import ru.itis.stylemap.repository.UserRepository;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DBUserService implements UserService {

    private final UserRepository userRepository;
    private final UserFollowsRepository userFollowsRepository;

    @Override
    public UserProfileDto getUserProfile(String targetUsername, String currentUsername) {
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new NoSuchElementException("Пользователь не найден"));

        long followersCount = userFollowsRepository.countByFollowing(targetUser);
        long followingCount = userFollowsRepository.countByFollower(targetUser);

        boolean isCurrentUserProfile = false;
        boolean isFollowing = false;

        if (currentUsername != null) {

            isCurrentUserProfile = currentUsername.equals(targetUsername);

            if (!isCurrentUserProfile) {
                User currentUser = userRepository.findByUsername(currentUsername)
                        .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
                isFollowing = userFollowsRepository.existsByFollowerAndFollowing(currentUser, targetUser);
            }
        }

        return new UserProfileDto(
                targetUser.getId(),
                targetUser.getUsername(),
                null,
                followersCount,
                followingCount,
                isCurrentUserProfile,
                isFollowing
        );
    }
}