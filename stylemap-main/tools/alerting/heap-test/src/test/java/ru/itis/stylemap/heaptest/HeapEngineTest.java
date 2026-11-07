package ru.itis.stylemap.heaptest;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static ru.itis.stylemap.heaptest.HeapEngine.MIB;

class HeapEngineTest {
    // These counters simulate memory. No byte arrays or real heap-retention run are used.
    static class FakeMemory implements HeapEngine.Memory, HeapEngine.Retainer {
        long baseline = 16 * MIB;
        long max = 126 * MIB;
        long containerLimit = HeapEngine.CONTAINER_LIMIT_BYTES;
        long containerExtra;
        long garbage;
        long retained;
        int allocations;
        int collections;
        int largest;
        boolean pools = true;
        boolean failRead;
        boolean failAllocation;

        public HeapEngine.Sample sample() {
            if (failRead) throw new IllegalStateException("Simulated monitor failure");
            return new HeapEngine.Sample(baseline + retained + garbage, max, max,
                    100 * MIB + retained + containerExtra, containerLimit, pools);
        }
        public void collect() { collections++; garbage = 0; }
        public long bytes() { return retained; }
        public void add(int size) {
            if (failAllocation) throw new OutOfMemoryError("Simulated allocation failure; no large allocation");
            largest = Math.max(largest, size);
            allocations++;
            retained += size;
        }
        public void clear() { retained = 0; }
    }

    private final AtomicLong clock = new AtomicLong();
    private final FakeMemory memory = new FakeMemory();
    private final HeapEngine engine = new HeapEngine(memory, memory, clock::get);

    private void advance(Duration duration) { clock.addAndGet(duration.toNanos()); engine.tick(); }

    private void reachTarget() {
        assertTrue(engine.start());
        for (int i = 0; i < 800 && engine.status().state() == HeapEngine.State.RAMPING; i++)
            advance(Duration.ofMillis(100));
        assertEquals(HeapEngine.State.HOLDING, engine.status().state());
    }

    @Test void idleNeverAllocates() {
        advance(Duration.ofHours(1));
        assertEquals(HeapEngine.State.IDLE, engine.status().state());
        assertEquals(0, memory.allocations);
        assertEquals(0, memory.collections);
    }

    @Test void allocationIsGradualAndStopsAtTheRealTarget() {
        reachTarget();
        assertTrue(engine.status().heapFraction() >= 0.87 && engine.status().heapFraction() <= 0.88);
        assertTrue(memory.largest <= HeapEngine.CHUNK_BYTES);
        assertTrue(memory.retained <= HeapEngine.MAX_RETAINED_BYTES);
        int calls = memory.allocations;
        advance(Duration.ofMinutes(5));
        assertEquals(HeapEngine.State.HOLDING, engine.status().state());
        assertEquals(calls, memory.allocations);
    }

    @Test void rapidTicksCannotCreateAnAllocationBurst() {
        assertTrue(engine.start());
        engine.tick();
        for (int i = 0; i < 100; i++) engine.tick();
        assertEquals(1, memory.allocations);
    }

    @Test void repeatedStartCannotExtendTheHoldDeadline() {
        reachTarget();
        advance(Duration.ofMinutes(4));
        assertFalse(engine.start());
        advance(Duration.ofMinutes(4));
        assertEquals(HeapEngine.State.EXPIRED, engine.status().state());
        assertEquals(0, memory.retained);
    }

    @Test void releaseDropsAllReferencesWithoutRestartingOrReallocating() {
        reachTarget();
        int calls = memory.allocations;
        assertEquals(HeapEngine.State.RELEASED, engine.release().state());
        assertEquals(0, memory.retained);
        advance(Duration.ofMinutes(1));
        assertEquals(calls, memory.allocations);
        assertEquals(HeapEngine.State.RELEASED, engine.release().state());
    }

    @Test void unsupportedPoolsRefuseStart() {
        memory.pools = false;
        assertFalse(engine.start());
        assertEquals(0, memory.allocations);
    }

    @Test void unboundedOrMismatchedCgroupRefusesStart() {
        for (long limit : new long[]{-1, Long.MAX_VALUE, 256 * MIB, 1024 * MIB}) {
            memory.containerLimit = limit;
            assertFalse(engine.start());
        }
        assertEquals(0, memory.allocations);
    }

    @Test void oversizedJvmOrHighBaselineRefusesStart() {
        memory.max = 1024 * MIB;
        assertFalse(engine.start());
        memory.max = 126 * MIB;
        memory.baseline = 96 * MIB;
        assertFalse(engine.start());
        assertEquals(0, memory.allocations);
    }

    @Test void containerPressureReleasesReferences() {
        reachTarget();
        memory.containerExtra = 300 * MIB;
        advance(Duration.ofSeconds(1));
        assertEquals(HeapEngine.State.ABORTED, engine.status().state());
        assertEquals("container_memory_guard", engine.status().reason());
        assertEquals(0, memory.retained);
    }

    @Test void hardHeapPressureReleasesReferences() {
        reachTarget();
        memory.garbage = 20 * MIB;
        advance(Duration.ofSeconds(1));
        assertEquals(HeapEngine.State.ABORTED, engine.status().state());
        assertEquals("heap_hard_guard", engine.status().reason());
        assertEquals(0, memory.retained);
    }

    @Test void ordinaryGarbageIsCollectedWithoutMoreRetainedAllocation() {
        reachTarget();
        int calls = memory.allocations;
        int collections = memory.collections;
        memory.garbage = 4 * MIB;
        advance(Duration.ofSeconds(1));
        assertEquals(HeapEngine.State.HOLDING, engine.status().state());
        assertEquals(collections + 1, memory.collections);
        assertEquals(calls, memory.allocations);
        assertTrue(engine.status().heapFraction() < 0.88);
    }

    @Test void absoluteRetentionCapAbortsInsteadOfChasingAnUnreachableTarget() {
        memory.baseline = 0;
        memory.max = 128 * MIB;
        assertTrue(engine.start());
        for (int i = 0; i < 600; i++) advance(Duration.ofMillis(100));
        assertEquals(HeapEngine.State.ABORTED, engine.status().state());
        assertEquals("allocation_budget_exhausted", engine.status().reason());
        assertTrue(memory.allocations <= HeapEngine.MAX_RETAINED_BYTES / HeapEngine.CHUNK_BYTES);
        assertEquals(0, memory.retained);
    }

    @Test void rampDeadlineAbortsAndReleases() {
        assertTrue(engine.start());
        engine.tick();
        advance(Duration.ofSeconds(90));
        assertEquals(HeapEngine.State.ABORTED, engine.status().state());
        assertEquals("ramp_deadline", engine.status().reason());
        assertEquals(0, memory.retained);
    }

    @Test void clockJumpPastOverallDeadlineReleases() {
        assertTrue(engine.start());
        engine.tick();
        advance(Duration.ofMinutes(10));
        assertEquals(HeapEngine.State.EXPIRED, engine.status().state());
        assertEquals(0, memory.retained);
    }

    @Test void simulatedAllocationFailureReleasesReferencesAndDoesNotRetry() {
        assertTrue(engine.start());
        engine.tick();
        memory.failAllocation = true;
        advance(Duration.ofMillis(100));
        int calls = memory.allocations;
        assertEquals(HeapEngine.State.ABORTED, engine.status().state());
        assertEquals(0, memory.retained);
        advance(Duration.ofSeconds(1));
        assertEquals(calls, memory.allocations);
    }

    @Test void monitorFailureReleasesReferences() {
        reachTarget();
        memory.failRead = true;
        advance(Duration.ofSeconds(1));
        assertEquals(HeapEngine.State.ABORTED, engine.status().state());
        assertEquals(0, memory.retained);
    }

    @Test void unexpectedLossOfTheHoldDoesNotAutomaticallyAllocateAgain() {
        reachTarget();
        int calls = memory.allocations;
        memory.baseline = 0;
        advance(Duration.ofSeconds(1));
        assertEquals(HeapEngine.State.ABORTED, engine.status().state());
        assertEquals(calls, memory.allocations);
        assertEquals(0, memory.retained);
    }
}
