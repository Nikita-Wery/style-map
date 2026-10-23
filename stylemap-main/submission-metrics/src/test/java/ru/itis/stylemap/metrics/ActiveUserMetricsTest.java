package ru.itis.stylemap.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import ru.itis.stylemap.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveUserMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void cachesStartupAndPeriodicCountsAndRetainsLastSuccessOnFailure() {
        var users = mock(UserRepository.class);
        when(users.countActiveUsers()).thenReturn(5L, 3L)
                .thenThrow(new IllegalStateException("database unavailable"));
        {
            var metrics = new ActiveUserMetrics(registry, users);
            var gauge = registry.get("stylemap.users.active").gauge();
            assertTrue(Double.isNaN(gauge.value()));
            metrics.onApplicationReady();
            assertEquals(5, gauge.value());
            assertEquals(5, gauge.value());
            verify(users, times(1)).countActiveUsers();
            metrics.refreshActiveUserCount();
            assertEquals(3, gauge.value());
            assertDoesNotThrow(metrics::refreshActiveUserCount);
            assertEquals(3, gauge.value());
        }
    }

    @Test
    void startupCountFailureDoesNotBreakStartupAndCanRecover() {
        var users = mock(UserRepository.class);
        when(users.countActiveUsers()).thenThrow(new IllegalStateException("not ready")).thenReturn(0L);
        {
            var metrics = new ActiveUserMetrics(registry, users);
            assertDoesNotThrow(metrics::onApplicationReady);
            assertTrue(Double.isNaN(registry.get("stylemap.users.active").gauge().value()));
            metrics.refreshActiveUserCount();
            assertEquals(0, registry.get("stylemap.users.active").gauge().value());
        }
    }
}
