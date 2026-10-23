package ru.itis.stylemap.service.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.itis.stylemap.controller.payload.NewUserPayload;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.entity.VerificationToken;
import ru.itis.stylemap.metrics.StyleMapBusinessMetrics;
import ru.itis.stylemap.repository.UserRepository;
import ru.itis.stylemap.repository.VerificationTokenRepository;
import ru.itis.stylemap.service.MailService;
import ru.itis.stylemap.service.UserAlreadyExistsException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock VerificationTokenRepository tokens;
    @Mock MailService mail;
    @Mock StyleMapBusinessMetrics metrics;
    @InjectMocks AuthService service;

    private final NewUserPayload payload = new NewUserPayload("reader", "reader@example.com", "password");

    @Test
    void registersInactiveUserWithEncodedPasswordAndEmailsPersistedTokenBeforeRecordingSuccess() {
        when(encoder.encode("password")).thenReturn("encoded-password");

        service.registerUser(payload);

        var savedUser = ArgumentCaptor.forClass(User.class);
        var savedToken = ArgumentCaptor.forClass(VerificationToken.class);
        var order = inOrder(users, tokens, mail, metrics);
        order.verify(users).saveAndFlush(savedUser.capture());
        order.verify(tokens).save(savedToken.capture());
        User user = savedUser.getValue();
        VerificationToken token = savedToken.getValue();
        assertEquals("reader", user.getUsername());
        assertEquals("reader@example.com", user.getEmail());
        assertEquals("encoded-password", user.getPassword());
        assertFalse(user.isActive());
        assertNull(user.getExternalAuthId());
        assertSame(user, token.getUser());
        // Assert the token contract, never a particular random value.
        assertTrue(token.getToken().matches("[1-9][0-9]{5}"));
        assertNotNull(token.getExpiryDate());
        order.verify(mail).sendConfirmationEmail(user.getEmail(), token.getToken());
        order.verify(metrics).userRegistered();
    }

    @Test
    void duplicateUserConstraintIsTranslatedAndStopsRegistration() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate username or email"));
        assertThrows(UserAlreadyExistsException.class, () -> service.registerUser(payload));
        verifyNoInteractions(tokens, mail, metrics);
    }

    @Test
    void tokenPersistenceFailureDoesNotEmailOrRecordSuccess() {
        when(tokens.save(any())).thenThrow(new IllegalStateException("token write failed"));
        assertThrows(IllegalStateException.class, () -> service.registerUser(payload));
        verify(users).saveAndFlush(any());
        verifyNoInteractions(mail, metrics);
    }

    @Test
    void emailFailureDoesNotRecordRegistrationSuccess() {
        doThrow(new IllegalStateException("mail unavailable")).when(mail).sendConfirmationEmail(eq(payload.email()), anyString());
        assertThrows(IllegalStateException.class, () -> service.registerUser(payload));
        verify(users).saveAndFlush(any());
        verify(tokens).save(any());
        verifyNoInteractions(metrics);
    }

    @Test
    void confirmsUserAndConsumesValidToken() {
        User user = inactiveUser();
        VerificationToken token = token(user, LocalDateTime.of(9999, 1, 1, 0, 0));
        when(tokens.findByUser(user)).thenReturn(Optional.of(token));

        assertTrue(service.confirmUser(payload.email(), "123456"));

        assertTrue(user.isActive());
        verify(tokens).delete(token);
        verifyNoInteractions(mail, metrics);
    }

    @Test
    void incorrectTokenLeavesUserInactiveAndTokenUnconsumed() {
        User user = inactiveUser();
        when(tokens.findByUser(user)).thenReturn(Optional.of(token(user, LocalDateTime.of(9999, 1, 1, 0, 0))));
        assertFalse(service.confirmUser(payload.email(), "654321"));
        assertUnconfirmed(user);
    }

    @Test
    void expiredTokenLeavesUserInactiveAndTokenUnconsumed() {
        User user = inactiveUser();
        when(tokens.findByUser(user)).thenReturn(Optional.of(token(user, LocalDateTime.of(2000, 1, 1, 0, 0))));
        assertFalse(service.confirmUser(payload.email(), "123456"));
        assertUnconfirmed(user);
    }

    @Test
    void missingUserFailsBeforeTokenLookup() {
        when(users.findByEmail(payload.email())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.confirmUser(payload.email(), "123456"));
        verifyNoInteractions(tokens, mail, metrics);
    }

    @Test
    void missingStoredTokenLeavesUserInactive() {
        User user = inactiveUser();
        when(tokens.findByUser(user)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.confirmUser(payload.email(), "123456"));
        assertUnconfirmed(user);
    }

    private User inactiveUser() {
        User user = new User();
        user.setEmail(payload.email());
        user.setActive(false);
        when(users.findByEmail(payload.email())).thenReturn(Optional.of(user));
        return user;
    }

    private VerificationToken token(User user, LocalDateTime expiry) {
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setToken("123456");
        token.setExpiryDate(expiry);
        return token;
    }

    private void assertUnconfirmed(User user) {
        assertFalse(user.isActive());
        verify(tokens, never()).delete(any());
        verify(users, never()).save(any());
        verifyNoInteractions(mail, metrics);
    }
}
