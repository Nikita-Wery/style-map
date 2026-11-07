package ru.itis.stylemap.heaptest;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FixtureManifestTest {
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) { return (List<Map<String, Object>>) value; }

    @Test void podIsScrapableButNeverReadyAndCannotMountProductionState() throws Exception {
        Path root = Path.of(System.getProperty("basedir", "."));
        Map<String, Object> pod = new Yaml().load(Files.readString(root.resolve("pod.yaml")));
        var metadata = map(pod.get("metadata"));
        assertEquals("stylemap-heap-test", metadata.get("name"));
        assertEquals("stylemap", metadata.get("namespace"));
        assertEquals("backend", map(metadata.get("labels")).get("app"));
        assertEquals("true", map(metadata.get("annotations")).get("prometheus.io/scrape"));
        assertEquals("19081", map(metadata.get("annotations")).get("prometheus.io/port"));
        var spec = map(pod.get("spec"));
        assertEquals("Never", spec.get("restartPolicy"));
        assertEquals(1200, spec.get("activeDeadlineSeconds"));
        assertEquals(false, spec.get("automountServiceAccountToken"));
        assertEquals("alerting.stylemap.io/traffic-enabled", list(spec.get("readinessGates")).getFirst().get("conditionType"));
        assertFalse(pod.containsKey("status")); // No True gate condition is injected.
        var container = list(spec.get("containers")).getFirst();
        assertFalse(container.containsKey("env"));
        assertFalse(container.containsKey("envFrom"));
        assertEquals("Never", container.get("imagePullPolicy"));
        assertEquals(19081, list(container.get("ports")).getFirst().get("containerPort"));
        assertEquals("heap-metrics", list(container.get("ports")).getFirst().get("name"));
        assertEquals(1, list(container.get("ports")).size());
        var resources = map(container.get("resources"));
        assertEquals("512Mi", map(resources.get("limits")).get("memory"));
        assertEquals("256Mi", map(resources.get("requests")).get("memory"));
        var arguments = (List<String>) container.get("args");
        for (String flag : List.of("-Xms128m", "-Xmx128m", "-XX:+UseSerialGC", "-XX:NewRatio=9")) {
            assertTrue(arguments.contains(flag));
            assertTrue(Files.readString(root.resolve("Dockerfile")).contains(flag));
        }
        for (var volume : list(spec.get("volumes"))) {
            assertTrue(volume.containsKey("emptyDir"));
            assertFalse(volume.containsKey("secret"));
            assertFalse(volume.containsKey("persistentVolumeClaim"));
        }
    }

    @Test void realAllocatorRejectsInvalidSizesWithoutAllocatingBlocks() {
        var retainer = new JvmMemory.ByteArrayRetainer();
        for (int size : new int[]{-1, 0, HeapEngine.CHUNK_BYTES + 1, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> retainer.add(size));
        assertEquals(0, retainer.bytes());
        retainer.clear();
        assertEquals(0, retainer.bytes());
    }
}
