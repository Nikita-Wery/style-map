package ru.itis.stylemap.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.repository.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBUserServiceTest {
    @Mock UserRepository users;
    @Mock UserFollowsRepository follows;
    @InjectMocks DBUserService service;

    @Test void anonymousProfileIncludesCountsWithoutRelationshipLookup() {
        User target = target();
        var profile = service.getUserProfile("target", null);
        assertEquals(target.getId(), profile.id());
        assertEquals("target", profile.username());
        assertEquals(7, profile.followersCount());
        assertEquals(3, profile.followingCount());
        assertFalse(profile.isCurrentUserProfile());
        assertFalse(profile.isFollowing());
        verify(follows, never()).existsByFollowerAndFollowing(any(), any());
        verify(users, times(1)).findByUsername(anyString());
    }
    @Test void ownProfileSkipsRelationshipLookup() {
        target();
        var profile = service.getUserProfile("target", "target");
        assertTrue(profile.isCurrentUserProfile());
        assertFalse(profile.isFollowing());
        verify(users, times(1)).findByUsername("target");
        verify(follows, never()).existsByFollowerAndFollowing(any(), any());
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void otherProfileReflectsFollowRelationship(boolean following) {
        User target = target();
        User current = new User();
        when(users.findByUsername("current")).thenReturn(Optional.of(current));
        when(follows.existsByFollowerAndFollowing(current, target)).thenReturn(following);
        var profile = service.getUserProfile("target", "current");
        assertFalse(profile.isCurrentUserProfile());
        assertEquals(following, profile.isFollowing());
    }
    @Test void missingTargetStopsBeforeCounts() {
        assertThrows(NoSuchElementException.class, () -> service.getUserProfile("missing", null));
        verifyNoInteractions(follows);
    }
    @Test void missingCurrentUserFailsRatherThanReportingRelationship() {
        target();
        assertThrows(RuntimeException.class, () -> service.getUserProfile("target", "missing"));
        verify(follows, never()).existsByFollowerAndFollowing(any(), any());
    }
    private User target() {
        User target = new User();
        target.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        target.setUsername("target");
        when(users.findByUsername("target")).thenReturn(Optional.of(target));
        when(follows.countByFollowing(target)).thenReturn(7L);
        when(follows.countByFollower(target)).thenReturn(3L);
        return target;
    }
}
