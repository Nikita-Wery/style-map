package ru.itis.stylemap.service.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.itis.stylemap.entity.*;
import ru.itis.stylemap.repository.UserRepository;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(users);

    @ParameterizedTest @CsvSource({"USER,true", "USER,false", "ADMIN,true", "ADMIN,false"})
    void mapsCredentialsRoleAndActivationToSecurityPrincipal(Role role, boolean active) {
        User user = new User();
        user.setUsername("reader");
        user.setPassword("encoded");
        user.setRole(role);
        user.setActive(active);
        when(users.findByUsername("reader")).thenReturn(Optional.of(user));
        var details = service.loadUserByUsername("reader");
        assertEquals("reader", details.getUsername());
        assertEquals("encoded", details.getPassword());
        assertEquals(active, details.isEnabled());
        assertEquals(java.util.List.of("ROLE_" + role), details.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }
    @Test void translatesMissingUserToAuthenticationException() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing"));
    }
}
