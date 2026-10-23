package ru.itis.stylemap.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itis.stylemap.repository.UserRepository;

import java.util.concurrent.atomic.AtomicLong;

@Component
@EnableScheduling
@Slf4j
public class ActiveUserMetrics {

    private final UserRepository userRepository;

    private final AtomicLong activeUsers = new AtomicLong(-1);

    public ActiveUserMetrics(MeterRegistry registry, UserRepository userRepository) {
        this.userRepository = userRepository;
        Gauge.builder("stylemap.users.active", activeUsers, count -> {
            long value = count.get();
            return value < 0 ? Double.NaN : value;
        }).description("Enabled accounts in the latest successful database snapshot")
                .register(registry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        refreshActiveUserCount();
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 15000)
    public synchronized void refreshActiveUserCount() {
        try {
            activeUsers.set(userRepository.countActiveUsers());
        } catch (RuntimeException ex) {
            log.warn("Could not refresh active user count; keeping the previous snapshot", ex);
        }
    }
}
