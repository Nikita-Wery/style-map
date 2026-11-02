package ru.itis.stylemap.service;

import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import ru.itis.stylemap.dto.PostUpdateDto;
import ru.itis.stylemap.dto.OutfitItemDto;
import ru.itis.stylemap.dto.LinkDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.stylemap.dto.FileInfo;
import ru.itis.stylemap.entity.*;
import ru.itis.stylemap.metrics.StyleMapBusinessMetrics;
import ru.itis.stylemap.repository.*;
import ru.itis.stylemap.service.storage.FileStorageService;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock UserRepository users;
    @Mock PostRepository posts;
    @Mock HashtagRepository hashtags;
    @Mock FileStorageService storage;
    @Mock UserFollowsRepository follows;
    @Mock RatingRepository ratings;
    @Mock StyleMapBusinessMetrics metrics;
    // Real JSON mapping keeps publication tests focused on observable input/output.
    private final ObjectMapper mapper = new ObjectMapper();
    private final User reader = user("00000000-0000-0000-0000-000000000001", "reader");
    private final User author = user("00000000-0000-0000-0000-000000000002", "author");
    private final UUID postId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final Principal principal = () -> "reader";

    private PostService service() {
        return new PostService(users, posts, hashtags, storage, mapper, follows, ratings, metrics);
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,0", "2500,2500", "5000,5000", "99999,5000"})
    void demoDelayIsBounded(long configured, long expected) {
        var service = service();
        service.configureDemoPublishDelay(configured);
        assertEquals(expected, org.springframework.test.util.ReflectionTestUtils
                .getField(service, "demoPublishDelayMs"));
    }

    @Test
    void disabledDemoDoesNotSleepEvenWhenInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertDoesNotThrow(() -> service().applyDemoPublishDelay());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void enabledDemoIsInsideTimerAndInterruptionDoesNotRecordSuccess() {
        var service = service();
        service.configureDemoPublishDelay(10);
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class,
                    () -> service.createPostWithDetails("author", null, "{}"));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(metrics).startOutfitPublication();
            verify(metrics, never()).outfitPublished(any());
            verifyNoInteractions(users, storage, posts);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void followsCreatorAndRecordsSuccessAfterSaving() {
        followUsers();
        var id = new UserFollowsId(reader.getId(), author.getId());
        when(follows.existsById(id)).thenReturn(false);

        assertEquals(Map.of("status", "success", "action", "followed"),
                service().toggleFollow(author.getId(), principal));

        var saved = ArgumentCaptor.forClass(UserFollows.class);
        var order = inOrder(follows, metrics);
        order.verify(follows).existsById(id);
        order.verify(follows).save(saved.capture());
        order.verify(metrics).creatorFollowed();
        assertEquals(id, saved.getValue().getId());
        assertSame(reader, saved.getValue().getFollower());
        assertSame(author, saved.getValue().getFollowing());
        verify(follows, never()).deleteById(any());
        verify(metrics, never()).creatorUnfollowed();
    }

    @Test
    void unfollowsCreatorAndRecordsSuccessAfterDeleting() {
        followUsers();
        var id = new UserFollowsId(reader.getId(), author.getId());
        when(follows.existsById(id)).thenReturn(true);

        assertEquals(Map.of("status", "success", "action", "unfollowed"),
                service().toggleFollow(author.getId(), principal));

        var order = inOrder(follows, metrics);
        order.verify(follows).deleteById(id);
        order.verify(metrics).creatorUnfollowed();
        verify(follows, never()).save(any());
        verify(metrics, never()).creatorFollowed();
    }

    @Test
    void missingFollowTargetDoesNotWriteOrRecordSuccess() {
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(users.findById(author.getId())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service().toggleFollow(author.getId(), principal));
        verifyNoInteractions(follows, metrics);
    }

    @Test
    void createsRatingAndRecalculatesAggregateBeforeRecordingSuccess() {
        Post post = ratingPost();
        var id = new RatingId(reader.getId(), postId);
        when(ratings.findById(id)).thenReturn(Optional.empty());

        assertEquals(Map.of("status", "success"), service().ratePost(postId, principal, (short) 8));

        var saved = ArgumentCaptor.forClass(Rating.class);
        var order = inOrder(ratings, posts, metrics);
        order.verify(ratings).save(saved.capture());
        order.verify(posts).recalculateRatingStats(postId);
        order.verify(metrics).ratingSubmitted();
        assertEquals(id, saved.getValue().getId());
        assertSame(reader, saved.getValue().getUser());
        assertSame(post, saved.getValue().getPost());
        assertEquals((short) 8, saved.getValue().getScore());
    }

    @Test
    void updatesExistingRatingRatherThanReplacingIt() {
        Post post = ratingPost();
        var existing = new Rating(reader, post, (short) 3);
        when(ratings.findById(existing.getId())).thenReturn(Optional.of(existing));

        service().ratePost(postId, principal, (short) 9);

        assertEquals((short) 9, existing.getScore());
        var order = inOrder(ratings, posts, metrics);
        order.verify(ratings).save(same(existing));
        order.verify(posts).recalculateRatingStats(postId);
        order.verify(metrics).ratingSubmitted();
    }

    @Test
    void missingRatingUserStopsBeforePostLookup() {
        when(users.findByUsername("reader")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service().ratePost(postId, principal, (short) 8));
        verifyNoInteractions(posts, ratings, metrics);
    }

    @Test
    void missingPostDoesNotPersistRatingOrRecordSuccess() {
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(posts.findById(postId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service().ratePost(postId, principal, (short) 8));
        verifyNoInteractions(ratings, metrics);
        verify(posts, never()).recalculateRatingStats(any());
    }

    @Test
    void failedRatingSaveDoesNotRecalculateOrRecordSuccess() {
        ratingPost();
        var failure = new IllegalStateException("write failed");
        when(ratings.save(any())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service().ratePost(postId, principal, (short) 8)));
        verify(posts, never()).recalculateRatingStats(any());
        verifyNoInteractions(metrics);
    }

    @Test
    void failedAggregateRecalculationDoesNotRecordSuccess() {
        ratingPost();
        doThrow(new IllegalStateException("aggregate failed")).when(posts).recalculateRatingStats(postId);
        assertThrows(IllegalStateException.class, () -> service().ratePost(postId, principal, (short) 8));
        verify(ratings).save(any());
        verifyNoInteractions(metrics);
    }

    @Test
    void publishesPostWithMediaItemsAndExistingAndNewHashtags() {
        when(users.findByUsername("author")).thenReturn(Optional.of(author));
        MultipartFile file = mock(MultipartFile.class);
        when(storage.saveFile(file)).thenReturn(new FileInfo("/uploads/look.jpg", "look.jpg", "image/jpeg", MediaType.IMAGE));
        var existing = new Hashtag();
        existing.setPath("style.casual");
        var created = new Hashtag();
        created.setPath("season.summer");
        when(hashtags.findByPath("style.casual")).thenReturn(Optional.of(existing));
        when(hashtags.findByPath("season.summer")).thenReturn(Optional.empty(), Optional.of(created));
        Timer.Sample sample = mock(Timer.Sample.class);
        when(metrics.startOutfitPublication()).thenReturn(sample);

        service().createPostWithDetails("author", file, """
                {"description":"Summer look","hashtags":["style.casual","season.summer"],
                 "outfitItems":[{"name":"Shirt","brand":"Example","links":{
                   "shop":{"url":"https://example.com/shirt","price":25,"currency":"USD"}}}]}
                """);

        var saved = ArgumentCaptor.forClass(Post.class);
        var order = inOrder(posts, metrics);
        order.verify(posts).save(saved.capture());
        order.verify(metrics).outfitPublished(sample);
        Post post = saved.getValue();
        assertSame(author, post.getAuthor());
        assertEquals("Summer look", post.getDescription());
        assertEquals(List.of(existing, created), post.getHashtags());
        verify(hashtags).insertHashtag("summer", "season.summer");
        verify(hashtags, never()).insertHashtag(anyString(), eq("style.casual"));
        assertEquals(1, post.getMediaFiles().size());
        var media = post.getMediaFiles().getFirst();
        assertSame(post, media.getPost());
        assertEquals("/uploads/look.jpg", media.getStoredFilePath());
        assertEquals("look.jpg", media.getOriginalFileName());
        assertEquals("image/jpeg", media.getMimeType());
        assertEquals(MediaType.IMAGE, media.getMediaType());
        assertEquals(1, post.getItems().size());
        var item = post.getItems().getFirst();
        assertSame(post, item.getPost());
        assertEquals("Shirt", item.getName());
        assertEquals("Example", item.getBrand());
        assertEquals("https://example.com/shirt", item.getLinks().get("shop").url());
        assertEquals("USD", item.getLinks().get("shop").currency());
        assertEquals(0, new BigDecimal("25").compareTo(item.getLinks().get("shop").price()));
    }

    @Test
    void malformedPublicationJsonFailsBeforeFileOrPostPersistence() {
        when(users.findByUsername("author")).thenReturn(Optional.of(author));
        var error = assertThrows(IllegalArgumentException.class,
                () -> service().createPostWithDetails("author", mock(MultipartFile.class), "{invalid"));
        assertEquals("Ошибка при чтении данных поста", error.getMessage());
        assertNotNull(error.getCause());
        verifyNoInteractions(storage, posts, hashtags);
        verify(metrics, never()).outfitPublished(any());
    }

    @Test
    void failedPublicationSaveDoesNotRecordSuccess() {
        when(users.findByUsername("author")).thenReturn(Optional.of(author));
        MultipartFile file = mock(MultipartFile.class);
        when(storage.saveFile(file)).thenReturn(new FileInfo("/uploads/look.jpg", "look.jpg", "image/jpeg", MediaType.IMAGE));
        when(posts.save(any())).thenThrow(new IllegalStateException("write failed"));
        assertThrows(IllegalStateException.class,
                () -> service().createPostWithDetails("author", file, "{\"description\":\"Look\"}"));
        verifyNoInteractions(hashtags);
        verify(metrics, never()).outfitPublished(any());
    }

    @ParameterizedTest
    @CsvSource({
            "date_asc,false,createdAt,ASC", "rating_desc,false,averageRating,DESC",
            "date_desc,false,createdAt,DESC", "unknown,false,createdAt,DESC",
            "date_asc,true,created_at,ASC", "rating_desc,true,average_rating,DESC",
            "date_desc,true,created_at,DESC", "unknown,true,created_at,DESC"})
    void publicFeedSelectsQueryAndSortAndMapsMedia(String sort, boolean filtered, String property, String direction) {
        var paging = PageRequest.of(1, 2,
                Sort.by(Sort.Direction.valueOf(direction), property));
        Post withMedia = new Post(author, "video");
        withMedia.setId(postId);
        withMedia.addMedia(new PostMedia("/uploads/first.mp4", "first.mp4", "video/mp4", MediaType.VIDEO));
        withMedia.addMedia(new PostMedia("/uploads/second.jpg", "second.jpg", "image/jpeg", MediaType.IMAGE));
        Post withoutMedia = new Post(author, "no media");
        var page = new PageImpl<>(List.of(withMedia, withoutMedia), paging, 7);
        String tag = filtered ? "style.casual" : null;
        if (filtered) when(posts.findAllByHashtagPath(tag, paging)).thenReturn(page);
        else when(posts.findAll(paging)).thenReturn(page);

        var result = service().getFeed(tag, 1, 2, sort, false, null);

        assertEquals(7, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertEquals(postId, result.getContent().getFirst().id());
        assertEquals("/uploads/first.mp4", result.getContent().getFirst().mediaUrl());
        assertEquals("VIDEO", result.getContent().getFirst().mediaType());
        assertEquals("", result.getContent().get(1).mediaUrl());
        assertEquals("IMAGE", result.getContent().get(1).mediaType());
        if (filtered) verify(posts).findAllByHashtagPath(tag, paging);
        else verify(posts).findAll(paging);
        verifyNoMoreInteractions(posts);
        verifyNoInteractions(users, follows);
    }

    @Test void blankHashtagUsesUnfilteredFeed() {
        var paging = PageRequest.of(0, 10,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        when(posts.findAll(paging)).thenReturn(Page.empty(paging));
        assertTrue(service().getFeed("  ", 0, 10, "date_desc", false, null).isEmpty());
        verify(posts).findAll(paging);
        verifyNoMoreInteractions(posts);
    }

    @Test void followingFeedWithNoFollowedUsersSkipsPostLookup() {
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(follows.findFollowingByFollower(reader)).thenReturn(List.of());
        assertTrue(service().getFeed(null, 0, 10, "date_desc", true, principal).isEmpty());
        verifyNoInteractions(posts);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void followingFeedRestrictsQueryToFollowedAuthors(boolean filtered) {
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(follows.findFollowingByFollower(reader)).thenReturn(List.of(author));
        var paging = PageRequest.of(0, 5,
                Sort.by(Sort.Direction.DESC,
                        filtered ? "created_at" : "createdAt"));
        if (filtered) when(posts.findByHashtagAndUserIn("casual", List.of("author"), paging))
                .thenReturn(Page.empty(paging));
        else when(posts.findByAuthorIn(List.of(author), paging))
                .thenReturn(Page.empty(paging));
        assertTrue(service().getFeed(filtered ? "casual" : null, 0, 5, "date_desc", true, principal).isEmpty());
        if (filtered) verify(posts).findByHashtagAndUserIn("casual", List.of("author"), paging);
        else verify(posts).findByAuthorIn(List.of(author), paging);
        verifyNoMoreInteractions(posts);
    }

    @Test void missingFollowingFeedUserDoesNotQueryPosts() {
        assertThrows(NoSuchElementException.class,
                () -> service().getFeed(null, 0, 5, "date_desc", true, principal));
        verifyNoInteractions(posts, follows);
    }

    @Test void userPostsUseDescendingDatePaginationAndMapContent() {
        var paging = PageRequest.of(0, 5,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Post post = new Post(author, "look");
        post.setId(postId);
        when(posts.findAllByAuthor_Id(author.getId(), paging)).thenReturn(
                new PageImpl<>(List.of(post), paging, 1));
        var result = service().getUserPosts(author.getId(), 0, 5);
        assertEquals(1, result.getTotalElements());
        assertEquals(postId, result.getContent().getFirst().id());
        assertEquals("", result.getContent().getFirst().mediaUrl());
        assertEquals("IMAGE", result.getContent().getFirst().mediaType());
        verify(posts).findAllByAuthor_Id(author.getId(), paging);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"missing", "author"})
    void postDetailsForAnonymousUnknownOrAuthorSkipFollowAndRatingLookup(String viewer) {
        Post post = detailsPost();
        if ("author".equals(viewer)) when(users.findByUsername(viewer)).thenReturn(Optional.of(author));
        var dto = service().getPostDetails(postId, viewer);
        assertEquals("author".equals(viewer), dto.isAuthor());
        assertFalse(dto.isFollowingAuthor());
        assertNull(dto.currentUserRating());
        assertEquals(postId, dto.id());
        assertEquals("Look", dto.description());
        assertEquals(author.getId(), dto.authorId());
        assertEquals("author", dto.authorUsername());
        assertEquals("/avatar.jpg", dto.authorAvatarUrl());
        assertEquals(post.getAverageRating(), dto.averageRating());
        assertEquals(List.of("style.casual"), dto.hashtagPaths());
        assertEquals("/uploads/look.jpg", dto.mediaFiles().getFirst().url());
        assertEquals("IMAGE", dto.mediaFiles().getFirst().type());
        assertEquals("Shirt", dto.outfitItems().getFirst().name());
        assertEquals("Brand", dto.outfitItems().getFirst().brand());
        assertEquals(post.getItems().getFirst().getLinks(), dto.outfitItems().getFirst().links());
        verifyNoInteractions(follows, ratings);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void postDetailsReflectViewerFollowAndOptionalRating(boolean hasRating) {
        detailsPost();
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(follows.existsByFollowerAndFollowing(reader, author)).thenReturn(hasRating);
        var id = new RatingId(reader.getId(), postId);
        Rating rating = new Rating();
        rating.setScore((short) 7);
        when(ratings.findById(id)).thenReturn(hasRating ? Optional.of(rating) : Optional.empty());
        var dto = service().getPostDetails(postId, "reader");
        assertFalse(dto.isAuthor());
        assertEquals(hasRating, dto.isFollowingAuthor());
        assertEquals(hasRating ? Short.valueOf((short) 7) : null, dto.currentUserRating());
        verify(ratings).findById(id);
        verify(follows).existsByFollowerAndFollowing(reader, author);
    }

    @Test void missingPostDetailsFailsBeforeUserLookup() {
        assertThrows(IllegalArgumentException.class, () -> service().getPostDetails(postId, "reader"));
        verifyNoInteractions(users, follows, ratings);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authorOrAdminCanDeletePostAndAllMediaBeforeRepositoryDeletion(boolean ownPost) {
        Post post = mutablePost();
        if (ownPost) post.setAuthor(reader);
        else reader.setRole(Role.ADMIN);
        PostMedia first = new PostMedia();
        PostMedia second = new PostMedia();
        post.addMedia(first);
        post.addMedia(second);
        var response = service().deletePost(postId, principal);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("status", "success"), response.getBody());
        var order = inOrder(storage, posts);
        order.verify(storage).deleteFile(first);
        order.verify(storage).deleteFile(second);
        order.verify(posts).delete(post);
    }

    @Test void unrelatedUserCannotDeletePost() {
        Post post = mutablePost();
        assertEquals(403, service().deletePost(postId, principal).getStatusCode().value());
        verifyNoInteractions(storage);
        verify(posts, never()).delete(post);
    }

    @Test void deletionStopsIfMediaServiceThrows() {
        Post post = mutablePost();
        post.setAuthor(reader);
        PostMedia media = new PostMedia();
        post.addMedia(media);
        var failure = new IllegalStateException("storage failed");
        doThrow(failure).when(storage).deleteFile(media);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service().deletePost(postId, principal)));
        verify(posts, never()).delete(any());
    }

    @Test void nonAuthorIncludingAdminCannotEditPost() {
        Post post = mutablePost();
        reader.setRole(Role.ADMIN);
        var response = service().updatePost(postId, new PostUpdateDto("changed", null, null), principal);
        assertEquals(403, response.getStatusCode().value());
        assertEquals("Look", post.getDescription());
        verify(posts, never()).save(any());
        verifyNoInteractions(hashtags);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authorUpdateReplacesItemsAndTagsOrClearsThemWhenNull(boolean populated) {
        Post post = mutablePost();
        post.setAuthor(reader);
        post.getHashtags().add(new Hashtag());
        post.addItem(new OutfitItem());
        Hashtag existing = new Hashtag();
        Hashtag created = new Hashtag();
        if (populated) {
            when(hashtags.findByPath("style.casual")).thenReturn(Optional.of(existing));
            when(hashtags.findByPath("season.summer")).thenReturn(Optional.empty(), Optional.of(created));
        }
        var links = Map.of("shop", new LinkDto("https://example.com", BigDecimal.TEN, "USD"));
        var dto = new PostUpdateDto("changed",
                populated ? List.of("style.casual", "season.summer") : null,
                populated ? List.of(new OutfitItemDto("Shirt", "Brand", links)) : null);
        var response = service().updatePost(postId, dto, principal);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("status", "success"), response.getBody());
        assertEquals("changed", post.getDescription());
        verify(posts).save(same(post));
        if (populated) {
            assertEquals(List.of(existing, created), post.getHashtags());
            verify(hashtags).insertHashtag("summer", "season.summer");
            verify(hashtags, never()).insertHashtag(anyString(), eq("style.casual"));
            assertEquals(1, post.getItems().size());
            var item = post.getItems().getFirst();
            assertEquals("Shirt", item.getName());
            assertEquals("Brand", item.getBrand());
            assertEquals(links, item.getLinks());
            assertSame(post, item.getPost());
        } else {
            assertTrue(post.getHashtags().isEmpty());
            assertTrue(post.getItems().isEmpty());
            verifyNoInteractions(hashtags);
        }
    }

    @ParameterizedTest
    @CsvSource({"delete,true", "delete,false", "update,true", "update,false"})
    void missingUserOrPostPreventsMutation(String operation, boolean missingUser) {
        if (!missingUser) when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        assertThrows(NoSuchElementException.class, () -> {
            if (operation.equals("delete")) service().deletePost(postId, principal);
            else service().updatePost(postId, new PostUpdateDto("new", null, null), principal);
        });
        verify(posts, never()).save(any());
        verify(posts, never()).delete(any(Post.class));
        verifyNoInteractions(storage, hashtags);
        if (missingUser) verifyNoInteractions(posts);
    }

    @Test void publicationWithoutOptionalDetailsPersistsEmptyCollections() {
        when(users.findByUsername("author")).thenReturn(Optional.of(author));
        MultipartFile file = mock(MultipartFile.class);
        when(storage.saveFile(file)).thenReturn(new FileInfo("/uploads/look.jpg", "look.jpg", "image/jpeg", MediaType.IMAGE));
        service().createPostWithDetails("author", file, "{\"description\":\"Look\"}");
        var saved = ArgumentCaptor.forClass(Post.class);
        verify(posts).save(saved.capture());
        assertTrue(saved.getValue().getHashtags().isEmpty());
        assertTrue(saved.getValue().getItems().isEmpty());
        assertEquals(1, saved.getValue().getMediaFiles().size());
        verify(metrics).outfitPublished(null);
        verifyNoInteractions(hashtags);
    }

    private Post mutablePost() {
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        Post post = new Post(author, "Look");
        post.setId(postId);
        when(posts.findById(postId)).thenReturn(Optional.of(post));
        return post;
    }

    private Post detailsPost() {
        author.setAvatarUrl("/avatar.jpg");
        Post post = new Post(author, "Look");
        post.setId(postId);
        post.setAverageRating(new BigDecimal("7.50"));
        post.addMedia(new PostMedia("/uploads/look.jpg", "look.jpg", "image/jpeg", MediaType.IMAGE));
        Hashtag tag = new Hashtag();
        tag.setPath("style.casual");
        post.getHashtags().add(tag);
        OutfitItem item = new OutfitItem();
        item.setName("Shirt");
        item.setBrand("Brand");
        item.setLinks(Map.of("shop", new LinkDto("https://example.com", BigDecimal.TEN, "USD")));
        post.addItem(item);
        when(posts.findById(postId)).thenReturn(Optional.of(post));
        return post;
    }

    private void followUsers() {
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(users.findById(author.getId())).thenReturn(Optional.of(author));
    }

    private Post ratingPost() {
        when(users.findByUsername("reader")).thenReturn(Optional.of(reader));
        Post post = new Post(author, "Look");
        post.setId(postId);
        when(posts.findById(postId)).thenReturn(Optional.of(post));
        return post;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(UUID.fromString(id));
        user.setUsername(username);
        return user;
    }
}
