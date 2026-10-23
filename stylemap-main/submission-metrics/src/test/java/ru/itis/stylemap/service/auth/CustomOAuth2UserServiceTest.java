package ru.itis.stylemap.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.*;
import org.springframework.web.client.RestOperations;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.repository.UserRepository;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock RestOperations http;
    private CustomOAuth2UserService service;
    private OAuth2UserRequest request;

    @BeforeEach void setup() {
        service = new CustomOAuth2UserService(users, encoder);
        service.setRestOperations(http);
        var registration = ClientRegistration.withRegistrationId("google").clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.com/callback").authorizationUri("https://example.com/auth")
                .tokenUri("https://example.com/token").userInfoUri("https://example.com/userinfo")
                .userNameAttributeName("sub").build();
        request = new OAuth2UserRequest(registration, new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", null, null));
    }
    private void providerResponse() {
        when(http.exchange(any(RequestEntity.class), ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("sub", "google-id", "name", "Test Reader", "email", "reader@example.com")));
    }
    @Test void existingExternalAccountUsesStoredUsernameWithoutEmailLookupOrSave() {
        providerResponse();
        User existing = user("stored-name");
        when(users.findByExternalAuthId("google-id")).thenReturn(Optional.of(existing));
        var result = service.loadUser(request);
        assertEquals("stored-name", result.getName());
        assertEquals("google-id", result.getAttribute("sub"));
        assertEquals("reader@example.com", result.getAttribute("email"));
        assertFalse(result.getAuthorities().isEmpty());
        verify(users, never()).findByEmail(anyString());
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }
    @Test void matchingEmailLinksExistingAccountWithoutChangingItsPasswordOrActivation() {
        providerResponse();
        User existing = user("local-name");
        existing.setPassword("old-password");
        existing.setActive(false);
        when(users.findByEmail("reader@example.com")).thenReturn(Optional.of(existing));
        when(users.save(existing)).thenReturn(existing);
        assertEquals("local-name", service.loadUser(request).getName());
        assertEquals("google-id", existing.getExternalAuthId());
        assertEquals("old-password", existing.getPassword());
        assertFalse(existing.isActive());
        verify(users).save(existing);
        verifyNoInteractions(encoder);
    }
    @Test void newAccountIsActiveWithNormalizedUsernameAndEncodedGeneratedPassword() {
        providerResponse();
        when(encoder.encode(anyString())).thenReturn("encoded-random-password");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.loadUser(request);
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User user = saved.getValue();
        assertEquals("reader@example.com", user.getEmail());
        assertEquals("google-id", user.getExternalAuthId());
        assertTrue(user.isActive());
        assertTrue(user.getUsername().matches("test_reader[0-9a-f]{8}"));
        assertEquals("encoded-random-password", user.getPassword());
        assertEquals(user.getUsername(), result.getName());
        var password = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(password.capture());
        assertDoesNotThrow(() -> UUID.fromString(password.getValue()));
    }
    @Test void providerFailureDoesNotAccessApplicationAccounts() {
        when(http.exchange(any(RequestEntity.class), ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenThrow(new org.springframework.web.client.RestClientException("offline"));
        assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));
        verifyNoInteractions(users, encoder);
    }
    @Test void accountPersistenceFailurePropagates() {
        providerResponse();
        User existing = user("local");
        when(users.findByEmail("reader@example.com")).thenReturn(Optional.of(existing));
        var failure = new IllegalStateException("write failed");
        when(users.save(existing)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.loadUser(request)));
        verifyNoInteractions(encoder);
    }
    private User user(String name) { User user = new User(); user.setUsername(name); return user; }
}
