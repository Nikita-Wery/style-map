package ru.itis.stylemap.heaptest;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Reads native MXBeans/cgroups; Actuator, not this class, exports the metrics. */
final class JvmMemory implements HeapEngine.Memory {
    private static final Set<String> EXPECTED = Set.of("Eden Space", "Survivor Space", "Tenured Gen");

    @Override
    public HeapEngine.Sample sample() {
        long used = 0;
        long max = 0;
        boolean valid = true;
        Set<String> names = new HashSet<>();
        for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            var usage = pool.getUsage();
            if (!names.add(pool.getName()) || usage == null || usage.getMax() <= 0
                    || usage.getUsed() < 0 || usage.getUsed() > usage.getMax()) {
                valid = false;
                continue;
            }
            used = Math.addExact(used, usage.getUsed());
            max = Math.addExact(max, usage.getMax());
        }
        long[] cgroup = cgroupMemory();
        return new HeapEngine.Sample(used, max, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax(),
                cgroup[0], cgroup[1], valid && names.equals(EXPECTED));
    }

    private long[] cgroupMemory() {
        try {
            Path current = Path.of("/sys/fs/cgroup/memory.current");
            if (Files.isReadable(current)) {
                return new long[]{number(current), number(Path.of("/sys/fs/cgroup/memory.max"))};
            }
            return new long[]{number(Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes")),
                    number(Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes"))};
        } catch (IOException | NumberFormatException failure) {
            return new long[]{-1, -1}; // Missing/unlimited/unreadable cgroups fail closed.
        }
    }

    private long number(Path path) throws IOException { return Long.parseLong(Files.readString(path).trim()); }

    @Override
    public void collect() { System.gc(); }

    static final class ByteArrayRetainer implements HeapEngine.Retainer {
        private final ArrayList<byte[]> blocks = new ArrayList<>();
        private long bytes;

        @Override
        public long bytes() { return bytes; }

        @Override
        public void add(int size) {
            if (size <= 0 || size > HeapEngine.CHUNK_BYTES || bytes + size > HeapEngine.MAX_RETAINED_BYTES)
                throw new IllegalArgumentException("Allocation outside the fixed fixture budget");
            byte[] block = new byte[size];
            for (int index = 0; index < size; index += 4096) block[index] = 1;
            blocks.add(block);
            bytes += size;
        }

        @Override
        public void clear() {
            blocks.clear();
            blocks.trimToSize();
            bytes = 0;
        }
    }
}
