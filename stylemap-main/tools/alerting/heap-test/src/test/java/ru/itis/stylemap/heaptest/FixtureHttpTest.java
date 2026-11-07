package ru.itis.stylemap.heaptest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FixtureHttpTest {
    @Test void realActuatorExportsNativeJvmMetricsWhileRetentionRemainsIdle() throws Exception {
        // Real local servers and JVM metric reads, but NEVER call /heap/start or allocate retained blocks.
        try (var context = new SpringApplicationBuilder(HeapTestApplication.class).run(
                "--server.port=0", "--management.server.port=0", "--management.server.address=127.0.0.1")) {
            int control = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            int management = context.getEnvironment().getRequiredProperty("local.management.port", Integer.class);
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var status = get(client, control, "/heap/status");
            assertEquals(200, status.statusCode());
            assertTrue(status.body().contains("\"state\":\"IDLE\""));
            var metrics = get(client, management, "/actuator/prometheus");
            assertEquals(200, metrics.statusCode());
            assertTrue(metrics.body().contains("jvm_memory_used_bytes{"));
            assertTrue(metrics.body().contains("jvm_memory_max_bytes{"));
            assertFalse(metrics.body().contains("stylemap_outfits_"));
            assertEquals(404, get(client, management, "/heap/status").statusCode());
            assertEquals(HeapEngine.State.IDLE, context.getBean(HeapEngine.class).status().state());
            assertEquals(0, context.getBean(HeapEngine.class).status().retainedBytes());
        }
    }

    @Test void nonLoopbackClientsCannotArmOrReleaseTheController() {
        var memory = new HeapEngineTest.FakeMemory();
        var engine = new HeapEngine(memory, memory, new AtomicLong()::get);
        var controller = new HeapController(engine);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        assertThrows(ResponseStatusException.class, () -> controller.start(request));
        assertThrows(ResponseStatusException.class, () -> controller.release(request));
        assertThrows(ResponseStatusException.class, () -> controller.status(request));
        assertEquals(HeapEngine.State.IDLE, engine.status().state());
        assertEquals(0, memory.allocations);
    }

    @Test void controlEndpointsUseTheSafetyStateMachineWithSimulatedMemoryOnly() {
        var memory = new HeapEngineTest.FakeMemory();
        var controller = new HeapController(new HeapEngine(memory, memory, new AtomicLong()::get));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        assertEquals(202, controller.start(request).getStatusCode().value());
        assertEquals(409, controller.start(request).getStatusCode().value());
        assertEquals(HeapEngine.State.RELEASED, controller.release(request).state());
        assertEquals(0, memory.allocations);
    }

    private HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
