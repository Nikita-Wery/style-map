package ru.itis.stylemap.heaptest;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Independent safety state machine. Tests replace both memory sampling and allocation. */
public final class HeapEngine implements AutoCloseable {
    public static final long MIB = 1024L * 1024;
    public static final int CHUNK_BYTES = 256 * 1024;
    public static final long MAX_RETAINED_BYTES = 104 * MIB;
    public static final long CONTAINER_LIMIT_BYTES = 512 * MIB;
    public static final double TARGET = 0.875;
    private static final double MIN_HOLD = 0.87;
    private static final double MAX_HOLD = 0.88;
    private static final double COLLECT_AT = 0.90;
    private static final double ABORT_AT = 0.92;
    private static final long STEP = Duration.ofMillis(100).toNanos();
    private static final long HOLD_CHECK = Duration.ofSeconds(1).toNanos();
    private static final long MAX_RAMP = Duration.ofSeconds(90).toNanos();
    private static final long MAX_HOLD_TIME = Duration.ofMinutes(8).toNanos();
    private static final long MAX_RUN = Duration.ofMinutes(10).toNanos();

    public enum State { IDLE, RAMPING, HOLDING, RELEASED, EXPIRED, ABORTED }

    public record Sample(long used, long max, long heapCapacity, long containerUsed,
                         long containerLimit, boolean validPools) {
        public double fraction() { return max > 0 ? (double) used / max : Double.NaN; }
    }

    public interface Memory {
        Sample sample();
        void collect();
    }

    public interface Retainer {
        long bytes();
        void add(int bytes);
        void clear();
    }

    public record Status(State state, String reason, long retainedBytes, double targetFraction,
                         Double heapFraction, long heapUsedBytes, long heapMaxBytes,
                         long cgroupUsedBytes, long cgroupLimitBytes, boolean poolsValid,
                         long remainingSeconds) { }

    private final Memory memory;
    private final Retainer retained;
    private final LongSupplier clock;
    private State state = State.IDLE;
    private String reason = "not_started";
    private long started;
    private long holdingSince;
    private long nextCheck;
    private int calibrations;

    public HeapEngine(Memory memory, Retainer retained, LongSupplier clock) {
        this.memory = memory;
        this.retained = retained;
        this.clock = clock;
    }

    public synchronized boolean start() {
        if (active()) return false; // A second request cannot extend a deadline.
        retained.clear();
        try {
            Sample sample = memory.sample();
            String problem = invalid(sample);
            if (problem != null) {
                finish(State.ABORTED, problem, false);
                return false;
            }
            // A clean baseline prevents temporary startup garbage becoming the target.
            memory.collect();
            sample = memory.sample();
            problem = invalid(sample);
            if (problem != null || sample.fraction() >= 0.70) {
                finish(State.ABORTED, problem == null ? "baseline_too_high" : problem, false);
                return false;
            }
            started = clock.getAsLong();
            nextCheck = started;
            calibrations = 0;
            state = State.RAMPING;
            reason = "gradual_allocation";
            return true;
        } catch (RuntimeException | OutOfMemoryError failure) {
            finish(State.ABORTED, "start_monitor_failed", true);
            return false;
        }
    }

    /** Called by the scheduler; does nothing while idle or after release. */
    public synchronized void tick() {
        if (!active()) return;
        long now = clock.getAsLong();
        if (now - started >= MAX_RUN || (state == State.HOLDING && now - holdingSince >= MAX_HOLD_TIME)) {
            finish(State.EXPIRED, "automatic_release_deadline", true);
            return;
        }
        if (now < nextCheck) return;
        nextCheck = now + (state == State.HOLDING ? HOLD_CHECK : STEP);
        try {
            Sample sample = memory.sample();
            String problem = invalid(sample);
            if (problem != null) {
                finish(State.ABORTED, problem, true);
                return;
            }
            if (state == State.HOLDING) {
                // Ordinary scrape/monitor garbage must be collected before it consumes headroom.
                if (sample.fraction() >= COLLECT_AT) {
                    memory.collect();
                    sample = memory.sample();
                    problem = invalid(sample);
                    if (problem != null || sample.fraction() >= COLLECT_AT) {
                        finish(State.ABORTED, problem == null ? "persistent_heap_pressure" : problem, true);
                        return;
                    }
                }
                if (sample.fraction() <= 0.85) finish(State.ABORTED, "hold_dropped_below_alert_threshold", true);
                return; // Never allocate another retained object after reaching HOLDING.
            }
            if (now - started >= MAX_RAMP) {
                finish(State.ABORTED, "ramp_deadline", true);
                return;
            }
            if (sample.fraction() >= TARGET) {
                if (++calibrations > 4) {
                    finish(State.ABORTED, "target_not_stable", true);
                    return;
                }
                memory.collect();
                sample = memory.sample();
                problem = invalid(sample);
                if (problem != null || sample.fraction() > MAX_HOLD) {
                    finish(State.ABORTED, problem == null ? "target_overshoot" : problem, true);
                    return;
                }
                if (sample.fraction() >= MIN_HOLD) {
                    state = State.HOLDING;
                    reason = "target_reached_no_more_allocation";
                    holdingSince = clock.getAsLong();
                    nextCheck = holdingSince + HOLD_CHECK;
                    return;
                }
            }
            long wanted = (long) Math.ceil(TARGET * sample.max() - sample.used());
            int bytes = (int) Math.min(CHUNK_BYTES, Math.max(1, wanted));
            if (retained.bytes() + bytes > MAX_RETAINED_BYTES
                    || sample.max() - sample.used() - bytes < 12 * MIB) {
                finish(State.ABORTED, "allocation_budget_exhausted", true);
                return;
            }
            retained.add(bytes); // Only allocation site; at most one small chunk per 100ms.
        } catch (RuntimeException | OutOfMemoryError failure) {
            // Defensive last resort, not a planned way to reach the target.
            finish(State.ABORTED, "allocation_or_monitor_failed", true);
        }
    }

    private String invalid(Sample sample) {
        if (!sample.validPools() || sample.max() <= 0 || sample.used() < 0
                || sample.heapCapacity() < 96 * MIB || sample.heapCapacity() > 128 * MIB
                || sample.max() > sample.heapCapacity()) return "unsupported_heap_or_pool_configuration";
        // Refuse unbounded/local-host execution and a mismatched pod memory limit.
        if (sample.containerLimit() != CONTAINER_LIMIT_BYTES || sample.containerUsed() < 0)
            return "requires_512Mi_container_cgroup";
        if (sample.containerUsed() >= sample.containerLimit() * 0.70) return "container_memory_guard";
        if (sample.fraction() >= ABORT_AT) return "heap_hard_guard";
        return null;
    }

    public synchronized Status release() {
        finish(State.RELEASED, "manual_release", active() || retained.bytes() > 0);
        return status();
    }

    public synchronized Status status() {
        Sample sample;
        try {
            sample = memory.sample();
        } catch (RuntimeException | OutOfMemoryError failure) {
            if (active()) finish(State.ABORTED, "status_monitor_failed", true);
            sample = new Sample(-1, -1, -1, -1, -1, false);
        }
        long remaining = active() ? Math.max(0, (MAX_RUN - (clock.getAsLong() - started)) / 1_000_000_000) : 0;
        if (state == State.HOLDING)
            remaining = Math.min(remaining, Math.max(0, (MAX_HOLD_TIME - (clock.getAsLong() - holdingSince)) / 1_000_000_000));
        double fraction = sample.fraction();
        return new Status(state, reason, retained.bytes(), TARGET, Double.isFinite(fraction) ? fraction : null,
                sample.used(), sample.max(), sample.containerUsed(), sample.containerLimit(), sample.validPools(), remaining);
    }

    private boolean active() { return state == State.RAMPING || state == State.HOLDING; }

    private void finish(State terminal, String why, boolean collect) {
        retained.clear();
        state = terminal;
        reason = why;
        if (collect) {
            try {
                memory.collect();
            } catch (RuntimeException | OutOfMemoryError ignored) {
                reason = "references_released_collection_failed";
            }
        }
    }

    @Override
    public synchronized void close() { finish(State.RELEASED, "shutdown", false); }
}
