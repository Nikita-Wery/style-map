package ru.itis.stylemap.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.stylemap.dto.*;
import ru.itis.stylemap.entity.*;
import ru.itis.stylemap.repository.*;
import ru.itis.stylemap.service.storage.FileStorageService;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final HashtagRepository hashtagRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final UserFollowsRepository userFollowsRepository;
    private final RatingRepository ratingRepository;

    private static final Logger log = LoggerFactory.getLogger("MAIN_LOGGER");

    @Transactional
    public void createPostWithDetails(String username, MultipartFile file, String postDataJson) {
        var user = userRepository.findByUsername(username).orElseThrow();
        PostCreateDto dto;

        try {
            dto = objectMapper.readValue(postDataJson, PostCreateDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ошибка при чтении данных поста", e);
        }

        Post post = new Post(user, dto.description());
        FileInfo fileInfo = fileStorageService.saveFile(file);
        PostMedia media = new PostMedia(fileInfo.storedFilePath(), fileInfo.originalFileName(), fileInfo.mimeType(), fileInfo.mediaType());
        post.addMedia(media);

        if (dto.outfitItems() != null) {
            for (OutfitItemDto itemDto : dto.outfitItems()) {
                OutfitItem item = new OutfitItem();
                item.setName(itemDto.name());
                item.setBrand(itemDto.brand());
                item.setLinks(itemDto.links());
                post.addItem(item);
            }
        }

        if (dto.hashtags() != null) {
            for (String tagPath : dto.hashtags()) {
                String[] parts = tagPath.split("\\.");
                String name = parts[parts.length - 1];
                Hashtag hashtag = hashtagRepository.findByPath(tagPath).orElseGet(() -> {
                    hashtagRepository.insertHashtag(name, tagPath);
                    return hashtagRepository.findByPath(tagPath).orElseThrow();
                });
                post.getHashtags().add(hashtag);
            }
        }
        postRepository.save(post);
    }

    @Transactional(readOnly = true)
    public Page<FeedPostDto> getFeed(String hashtag, int page, int size, String sortBy, boolean followingOnly, Principal principal) {
        Sort sort;
        switch (sortBy) {
            case "date_asc":
                if (hashtag != null && !hashtag.isBlank()) sort = Sort.by(Sort.Direction.ASC, "created_at");
                else sort = Sort.by(Sort.Direction.ASC, "createdAt");
                break;
            case "rating_desc":
                if (hashtag != null && !hashtag.isBlank()) sort = Sort.by(Sort.Direction.DESC, "average_rating");
                else sort = Sort.by(Sort.Direction.DESC, "averageRating");
                break;
            case "date_desc":
            default:
                if (hashtag != null && !hashtag.isBlank()) sort = Sort.by(Sort.Direction.DESC, "created_at");
                else sort = Sort.by(Sort.Direction.DESC, "createdAt");
                break;
        }
        PageRequest pageRequest = PageRequest.of(page, size, sort);;
        Page<Post> postsPage;
        if (followingOnly){
            User cur = userRepository.findByUsername(principal.getName()).orElseThrow();
            List<User> followedUsers = userFollowsRepository.findFollowingByFollower(cur);
            if (followedUsers.isEmpty()) {
                return Page.empty();
            }
            if (hashtag != null && !hashtag.isBlank()) {
                List<String> userIds = followedUsers.stream().map(User::getUsername).toList();
                postsPage = postRepository.findByHashtagAndUserIn(hashtag, userIds, pageRequest);
            } else {
                postsPage = postRepository.findByAuthorIn(followedUsers, pageRequest);
            }
        }
        else{
            if (hashtag != null && !hashtag.isBlank()) {
                postsPage = postRepository.findAllByHashtagPath(hashtag, pageRequest);
            } else {
                postsPage = postRepository.findAll(pageRequest);
            }
        }
        return getFeedPostDtos(postsPage);
    }

    @Transactional(readOnly = true)
    public PostDetailsDto getPostDetails(UUID postId, String currentUsername) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Пост не найден"));
        User currentUser = currentUsername != null ?
                userRepository.findByUsername(currentUsername).orElse(null) : null;
        boolean isFollowing = false;
        Short userRating = null;
        boolean isAuthor = false;

        if (currentUser != null) {
            isAuthor = currentUser.getId().equals(post.getAuthor().getId());

            if (!isAuthor) {
                isFollowing = userFollowsRepository.existsByFollowerAndFollowing(currentUser, post.getAuthor());

                Optional<Rating> ratingOpt = ratingRepository.findById(new RatingId(currentUser.getId(), post.getId()));
                if (ratingOpt.isPresent()) {
                    userRating = ratingOpt.get().getScore();
                }
            }
        }

        return new PostDetailsDto(
                post.getId(),
                post.getDescription(),
                post.getAuthor().getUsername(),
                post.getAuthor().getId(),
                post.getAuthor().getAvatarUrl(),
                isFollowing,
                post.getAverageRating(),
                userRating,
                post.getMediaFiles().stream()
                        .map(m -> new PostMediaDto(m.getStoredFilePath(), m.getMediaType().name()))
                        .collect(Collectors.toList()),
                post.getHashtags().stream()
                        .map(Hashtag::getPath)
                        .collect(Collectors.toList()),
                post.getItems().stream()
                        .map(item -> new OutfitItemDto(
                                item.getName(),
                                item.getBrand(),
                                item.getLinks().entrySet().stream().collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> new LinkDto(e.getValue().url(), e.getValue().price(), e.getValue().currency())
                                ))
                        ))
                        .collect(Collectors.toList()),
                isAuthor

        );
    }

    @Transactional
    public Map<String, Object> toggleFollow(UUID authorId, Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        User author = userRepository.findById(authorId).orElseThrow();

        UserFollowsId followId = new UserFollowsId(currentUser.getId(), author.getId());

        if (userFollowsRepository.existsById(followId)) {
            userFollowsRepository.deleteById(followId);
            return Map.of("status", "success", "action", "unfollowed");
        } else {
            UserFollows newFollow = new UserFollows(currentUser, author);
            userFollowsRepository.save(newFollow);
            return Map.of("status", "success", "action", "followed");
        }
    }


    @Transactional
    public Map<String, Object> ratePost(UUID postId, Principal principal, Short score) {
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        Post post = postRepository.findById(postId).orElseThrow();

        RatingId ratingId = new RatingId(currentUser.getId(), post.getId());

        Rating rating = ratingRepository.findById(ratingId)
                .orElse(new Rating(currentUser, post, score));

        rating.setScore(score);
        ratingRepository.save(rating);
        postRepository.recalculateRatingStats(postId);
        return Map.of("status", "success");
    }

    @Transactional
    public ResponseEntity<?> deletePost(UUID postId, Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        Post post = postRepository.findById(postId).orElseThrow();

        if (!post.getAuthor().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body("У вас нет прав на удаление этого поста");
        }
        for (PostMedia postMedia : post.getMediaFiles()) {
            fileStorageService.deleteFile(postMedia);
        }
        postRepository.delete(post);

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @Transactional
    public ResponseEntity<?> updatePost(UUID postId, PostUpdateDto request, Principal principal) {

        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        Post post = postRepository.findById(postId).orElseThrow();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("У вас нет прав на редактирование");
        }
        post.setDescription(request.description());
        post.getHashtags().clear();

        if (request.hashtags() != null) {
            for (String tagPath : request.hashtags()) {
                String[] parts = tagPath.split("\\.");
                String name = parts[parts.length - 1];
                Hashtag hashtag = hashtagRepository.findByPath(tagPath).orElseGet(() -> {
                    hashtagRepository.insertHashtag(name, tagPath);
                    return hashtagRepository.findByPath(tagPath).orElseThrow();
                });
                post.getHashtags().add(hashtag);
            }
        }

        post.getItems().clear();
        if (request.outfitItems() != null){
            for (OutfitItemDto itemDto : request.outfitItems()){
                OutfitItem newItem = new OutfitItem();
                newItem.setName(itemDto.name());
                newItem.setBrand(itemDto.brand());
                newItem.setPost(post);
                newItem.setLinks(itemDto.links());
                post.addItem(newItem);
            }
        }
        postRepository.save(post);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @Transactional
    public Page<FeedPostDto> getUserPosts(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> postsPage = postRepository.findAllByAuthor_Id(userId, pageable);

        return getFeedPostDtos(postsPage);

    }

    private Page<FeedPostDto> getFeedPostDtos(Page<Post> postsPage) {
        Page<FeedPostDto> dtoPage = postsPage.map(post -> {
            String url = "";
            String type = "IMAGE";
            if (!post.getMediaFiles().isEmpty()) {
                url = post.getMediaFiles().get(0).getStoredFilePath();
                type = post.getMediaFiles().get(0).getMediaType().name();
            }
            return new FeedPostDto(post.getId(), url, type);
        });

        return dtoPage;
    }
}