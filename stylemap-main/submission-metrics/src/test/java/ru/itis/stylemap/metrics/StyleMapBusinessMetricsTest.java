package ru.itis.stylemap.metrics;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StyleMapBusinessMetricsTest {

    private final java.util.List<io.micrometer.core.instrument.MeterRegistry> registries = new java.util.ArrayList<>();

    private <T extends io.micrometer.core.instrument.MeterRegistry> T track(T registry) {
        registries.add(registry);
        return registry;
    }

    @AfterEach
    void closeRegistries() {
        registries.forEach(io.micrometer.core.instrument.MeterRegistry::close);
    }

    @Test
    void recordsOnlyAfterCommitAndIncludesCommitTime() {
        MockClock clock = new MockClock();
        var registry = track(new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock));
        {
            var metrics = new StyleMapBusinessMetrics(registry);
            var transactions = new TransactionTemplate(new TestTransactionManager(() -> clock.add(Duration.ofSeconds(2))));
            transactions.executeWithoutResult(status -> {
                var sample = metrics.startOutfitPublication();
                clock.add(Duration.ofSeconds(1));
                metrics.outfitPublished(sample);
                metrics.ratingSubmitted();
                metrics.creatorFollowed();
                metrics.creatorUnfollowed();
                metrics.userRegistered();
                assertEquals(0, registry.get("stylemap.outfits.published").counter().count());
                assertEquals(0, registry.get("stylemap.outfits.publish.duration").timer().count());
            });
            assertEquals(1, registry.get("stylemap.outfits.published").counter().count());
            assertEquals(1, registry.get("stylemap.ratings.submitted").counter().count());
            assertEquals(1, registry.get("stylemap.follows.actions").tag("action", "followed").counter().count());
            assertEquals(1, registry.get("stylemap.follows.actions").tag("action", "unfollowed").counter().count());
            assertEquals(1, registry.get("stylemap.users.registered").counter().count());
            assertEquals(1, registry.get("stylemap.outfits.publish.duration").timer().count());
            assertEquals(3, registry.get("stylemap.outfits.publish.duration").timer().totalTime(TimeUnit.SECONDS));
        }
    }

    @Test
    void rollbackAndCommitFailureDoNotRecordSuccess() {
        var registry = track(new SimpleMeterRegistry());
        {
            var metrics = new StyleMapBusinessMetrics(registry);
            new TransactionTemplate(new TestTransactionManager(() -> {})).executeWithoutResult(status -> {
                recordAll(metrics);
                status.setRollbackOnly();
            });
            var failingTransactions = new TransactionTemplate(new TestTransactionManager(() -> {
                throw new TransactionSystemException("commit failed");
            }));
            assertThrows(TransactionSystemException.class,
                    () -> failingTransactions.executeWithoutResult(status -> recordAll(metrics)));
            registry.getMeters().forEach(meter -> {
                if (meter instanceof io.micrometer.core.instrument.Counter counter) {
                    assertEquals(0, counter.count());
                }
            });
            assertEquals(0, registry.get("stylemap.outfits.publish.duration").timer().count());
        }
    }

    @Test
    void metricUpdateFailureDoesNotFailCommittedOperation() {
        var registry = track(spy(new SimpleMeterRegistry()));
        {
            var failingCounter = mock(io.micrometer.core.instrument.Counter.class);
            doThrow(new IllegalStateException("meter failed")).when(failingCounter).increment();
            doReturn(failingCounter).when(registry).counter("stylemap.outfits.published");
            var metrics = new StyleMapBusinessMetrics(registry);
            assertDoesNotThrow(() -> new TransactionTemplate(new TestTransactionManager(() -> {}))
                    .executeWithoutResult(status -> metrics.outfitPublished(metrics.startOutfitPublication())));
            assertEquals(1, registry.get("stylemap.outfits.publish.duration").timer().count());
        }
    }

    @Test
    void exportsApprovedPrometheusNamesAndOnlyBoundedActionTags() {
        var registry = track(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
        {
            var metrics = new StyleMapBusinessMetrics(registry);
            recordAll(metrics);
            var users = mock(ru.itis.stylemap.repository.UserRepository.class);
            when(users.countActiveUsers()).thenReturn(4L);
            var active = new ActiveUserMetrics(registry, users);
            active.onApplicationReady();
            String scrape = registry.scrape();
            for (String name : new String[]{"stylemap_outfits_published_total", "stylemap_ratings_submitted_total",
                    "stylemap_follows_actions_total", "stylemap_users_registered_total", "stylemap_users_active",
                    "stylemap_outfits_publish_duration_seconds_count", "stylemap_outfits_publish_duration_seconds_sum",
                    "stylemap_outfits_publish_duration_seconds_max"}) {
                assertTrue(scrape.contains(name), name);
            }
            assertTrue(scrape.contains("action=\"followed\""));
            assertTrue(scrape.contains("action=\"unfollowed\""));
            assertEquals(7, registry.getMeters().size()); // One meter for each follow action.
            registry.getMeters().forEach(meter -> assertTrue(meter.getId().getTags().stream()
                    .allMatch(tag -> tag.getKey().equals("action")
                            && (tag.getValue().equals("followed") || tag.getValue().equals("unfollowed")))));
            verify(users, times(1)).countActiveUsers(); // Scraping never queries the database.
        }
    }

    private static void recordAll(StyleMapBusinessMetrics metrics) {
        metrics.outfitPublished(metrics.startOutfitPublication());
        metrics.ratingSubmitted();
        metrics.creatorFollowed();
        metrics.creatorUnfollowed();
        metrics.userRegistered();
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        private final Runnable commit;

        TestTransactionManager(Runnable commit) {
            this.commit = commit;
        }

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { commit.run(); }
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
