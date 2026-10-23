package ru.itis.stylemap.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.itis.stylemap.controller.payload.NewUserPayload;
import ru.itis.stylemap.entity.*;
import ru.itis.stylemap.repository.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock UserRepository users;
    @Mock PostRepository posts;
    @Mock HashtagRepository hashtags;
    @Mock PasswordEncoder encoder;
    @Mock VerificationTokenRepository tokens;
    @InjectMocks AdminService service;
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void createsUserWithEncodedPasswordDefaultRoleAndExternalId() {
        when(encoder.encode("secret")).thenReturn("encoded");
        service.createUser(new NewUserPayload("reader", "reader@example.com", "secret"));
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User user = saved.getValue();
        assertEquals("reader", user.getUsername());
        assertEquals("reader@example.com", user.getEmail());
        assertEquals("encoded", user.getPassword());
        assertEquals(Role.USER, user.getRole());
        assertTrue(user.isActive());
        assertDoesNotThrow(() -> UUID.fromString(user.getExternalAuthId()));
        verifyNoInteractions(tokens);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void changesUserActivation(boolean active) {
        User user = new User();
        user.setActive(!active);
        when(users.findById(id)).thenReturn(Optional.of(user));
        service.banUser(id, active);
        assertEquals(active, user.isActive());
    }
    @Test void missingUserCannotBeBanned() {
        assertThrows(NoSuchElementException.class, () -> service.banUser(id, false));
    }
    @Test void createsPostForAuthorFoundByEmail() {
        User author = new User();
        when(users.findByEmail("author@example.com")).thenReturn(Optional.of(author));
        service.createPost("author@example.com", "Look");
        var saved = ArgumentCaptor.forClass(Post.class);
        verify(posts).save(saved.capture());
        assertSame(author, saved.getValue().getAuthor());
        assertEquals("Look", saved.getValue().getDescription());
    }
    @Test void missingAuthorPreventsPostCreation() {
        assertThrows(NoSuchElementException.class, () -> service.createPost("missing", "Look"));
        verifyNoInteractions(posts);
    }
    @Test void updatesExistingPostDescription() {
        Post post = new Post(new User(), "old");
        when(posts.findById(id)).thenReturn(Optional.of(post));
        service.updatePost(id, "new");
        assertEquals("new", post.getDescription());
    }
    @Test void missingPostCannotBeUpdated() {
        assertThrows(NoSuchElementException.class, () -> service.updatePost(id, "new"));
        verify(posts, never()).save(any());
    }
    @Test void updatesExistingHashtagNameWithoutChangingPath() {
        Hashtag tag = new Hashtag();
        tag.setName("old");
        tag.setPath("style.casual");
        when(hashtags.findById(1L)).thenReturn(Optional.of(tag));
        service.updateHashtag(1L, "new");
        assertEquals("new", tag.getName());
        assertEquals("style.casual", tag.getPath());
    }
    @Test void missingHashtagCannotBeUpdated() {
        assertThrows(NoSuchElementException.class, () -> service.updateHashtag(1L, "new"));
    }
    @Test void deletesAndFlushesTokensBeforeDeletingUser() {
        User user = new User();
        when(users.findById(id)).thenReturn(Optional.of(user));
        service.deleteUser(id);
        var order = inOrder(tokens, users);
        order.verify(tokens).deleteByUser(user);
        order.verify(tokens).flush();
        order.verify(users).delete(user);
    }
    @Test void missingUserDoesNotDeleteTokens() {
        assertThrows(NoSuchElementException.class, () -> service.deleteUser(id));
        verifyNoInteractions(tokens);
        verify(users, never()).delete(any());
    }
    @Test void tokenFlushFailurePreventsUserDeletion() {
        User user = new User();
        when(users.findById(id)).thenReturn(Optional.of(user));
        var failure = new IllegalStateException("flush failed");
        doThrow(failure).when(tokens).flush();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.deleteUser(id)));
        verify(users, never()).delete(any());
    }
}
