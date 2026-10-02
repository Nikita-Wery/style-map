package ru.itis.stylemap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PostDetailsDto(
        UUID id,
        String description,
        String authorUsername,
        UUID authorId,
        String authorAvatarUrl,
        @JsonProperty("isFollowingAuthor") boolean isFollowingAuthor,
        BigDecimal averageRating,
        Short currentUserRating,
        List<PostMediaDto> mediaFiles,
        List<String> hashtagPaths,
        List<OutfitItemDto> outfitItems,
        @JsonProperty("isAuthor") boolean isAuthor
) {}
