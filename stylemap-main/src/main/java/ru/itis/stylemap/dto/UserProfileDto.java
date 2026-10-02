package ru.itis.stylemap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String username,
        String avatarUrl,
        long followersCount,
        long followingCount,
        @JsonProperty("isCurrentUserProfile") boolean isCurrentUserProfile,
        @JsonProperty("isFollowing") boolean isFollowing
) {}
