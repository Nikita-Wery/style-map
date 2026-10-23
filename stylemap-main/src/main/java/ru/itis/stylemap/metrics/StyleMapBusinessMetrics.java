package ru.itis.stylemap.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class StyleMapBusinessMetrics {

    private final MeterRegistry registry;
    private final Counter outfitsPublished;
    private final Counter ratingsSubmitted;
    private final Counter followed;
    private final Counter unfollowed;
    private final Counter usersRegistered;
    private final Timer publishDuration;

    public StyleMapBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        outfitsPublished = registry.counter("stylemap.outfits.published");
        ratingsSubmitted = registry.counter("stylemap.ratings.submitted");
        followed = registry.counter("stylemap.follows.actions", "action", "followed");
        unfollowed = registry.counter("stylemap.follows.actions", "action", "unfollowed");
        usersRegistered = registry.counter("stylemap.users.registered");
        publishDuration = registry.timer("stylemap.outfits.publish.duration");
    }

    public Timer.Sample startOutfitPublication() {
        try {
            return Timer.start(registry);
        } catch (RuntimeException ex) {
            log.warn("Could not start outfit publication timing", ex);
            return null;
        }
    }

    public void outfitPublished(Timer.Sample sample) {
        afterSuccessfulCommit(() -> {
            safelyRecord(outfitsPublished::increment);
            if (sample != null) {
                safelyRecord(() -> sample.stop(publishDuration));
            }
        });
    }

    public void ratingSubmitted() {
        afterSuccessfulCommit(ratingsSubmitted::increment);
    }

    public void creatorFollowed() {
        afterSuccessfulCommit(followed::increment);
    }

    public void creatorUnfollowed() {
        afterSuccessfulCommit(unfollowed::increment);
    }

    public void userRegistered() {
        afterSuccessfulCommit(usersRegistered::increment);
    }

    private void afterSuccessfulCommit(Runnable update) {
        safelyRecord(() -> {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            safelyRecord(update);
                        }
                    }
                });
            } else if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                safelyRecord(update);
            }
        });
    }

    private void safelyRecord(Runnable update) {
        try {
            update.run();
        } catch (RuntimeException ex) {
            log.warn("Could not record StyleMap business metric", ex);
        }
    }
}
