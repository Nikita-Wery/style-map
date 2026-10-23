package ru.itis.stylemap.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import ru.itis.stylemap.repository.PostRepository;
import ru.itis.stylemap.entity.Post;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShowroomServiceTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final ShowroomService service = new ShowroomService(posts);

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   ", "\t\n"})
    void absentSearchReturnsAllPosts(String query) {
        var result = List.of(new Post());
        when(posts.findAll()).thenReturn(result);
        assertSame(result, service.getPosts(query));
        verify(posts, never()).findAllByDescriptionContaining(anyString());
    }
    @Test void searchPassesNonblankQueryUnchangedAndPreservesEmptyResult() {
        when(posts.findAllByDescriptionContaining(" summer ")).thenReturn(List.of());
        assertTrue(service.getPosts(" summer ").isEmpty());
        verify(posts).findAllByDescriptionContaining(" summer ");
        verify(posts, never()).findAll();
    }
}
