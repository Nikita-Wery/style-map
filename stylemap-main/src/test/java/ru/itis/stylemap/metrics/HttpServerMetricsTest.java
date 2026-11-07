package ru.itis.stylemap.metrics;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ServerHttpObservationFilter;
import ru.itis.stylemap.config.SecurityConfig;
import ru.itis.stylemap.controller.advice.GlobalExceptionHandler;
import ru.itis.stylemap.service.auth.CustomOAuth2UserService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HttpServerMetricsTest {
    @Test
    void recordsHttpStatusMetricsWithSecurityAndSeparateManagementPort() throws Exception {
        try (var context = new SpringApplicationBuilder(TestApplication.class).run(
                "--spring.config.location=optional:classpath:/http-metrics-test.properties",
                "--server.port=0", "--management.server.port=0",
                "--management.endpoints.web.exposure.include=health,info,prometheus",
                "--spring.security.oauth2.client.registration.google.client-id=test-client",
                "--spring.security.oauth2.client.registration.google.client-secret=test-only",
                "--spring.main.banner-mode=off")) {
            var server = (ServletWebServerApplicationContext) context;
            assertNotNull(server.getServletContext().getFilterRegistration("webMvcObservationFilter"));
            var registration = context.getBean("webMvcObservationFilter", FilterRegistrationBean.class);
            assertTrue(registration.isEnabled());
            assertInstanceOf(ServerHttpObservationFilter.class, registration.getFilter());
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            int managementPort = context.getEnvironment().getRequiredProperty("local.management.port", Integer.class);
            // The separate management context does not exercise the application servlet filter.
            assertEquals(200, get(client, managementPort, "/actuator/health").statusCode());
            assertEquals(200, get(client, managementPort, "/actuator/prometheus").statusCode());
            var managementOnlyScrape = get(client, managementPort, "/actuator/prometheus");
            assertFalse(managementOnlyScrape.body().contains("http_server_requests_seconds_count{"));
            assertFalse(managementOnlyScrape.body().contains("http_server_requests_seconds_sum{"));
            int port = server.getWebServer().getPort();
            assertEquals(200, get(client, port, "/api/auth/metrics-test/ok").statusCode());
            assertEquals(500, get(client, port, "/api/auth/metrics-test/error").statusCode());
            assertEquals(401, get(client, port, "/api/private").statusCode());
            assertEquals(302, get(client, port, "/private-page").statusCode());
            assertEquals(403, client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/auth/metrics-test/ok"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());

            var registry = context.getBean(PrometheusMeterRegistry.class);
            // Response delivery can finish just before the servlet filter records its timer.
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                for (String status : new String[]{"200", "500", "401", "302", "403"}) {
                    assertEquals(1L, registry.get("http.server.requests").tag("status", status)
                            .timers().stream().mapToLong(timer -> timer.count()).sum(), status);
                }
            });
            var scrape = get(client, managementPort, "/actuator/prometheus");
            assertEquals(200, scrape.statusCode());
            assertTrue(scrape.body().contains("http_server_requests_seconds_count{"));
            assertTrue(scrape.body().contains("http_server_requests_seconds_sum{"));
            assertTrue(scrape.body().lines().anyMatch(line ->
                    line.startsWith("http_server_requests_seconds_count{") && line.contains("status=\"500\"")));
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
            "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration",
            "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration"})
    @Import({SecurityConfig.class, GlobalExceptionHandler.class, TestController.class})
    static class TestApplication {
        @Bean
        CustomOAuth2UserService customOAuth2UserService() {
            return mock(CustomOAuth2UserService.class);
        }

        @Bean
        InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(User.withUsername("metrics-test")
                    .password("{noop}test-only").roles("USER").build());
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/api/auth/metrics-test/ok")
        String ok() { return "ok"; }

        @GetMapping("/api/auth/metrics-test/error")
        String error() { throw new IllegalStateException("HTTP metrics regression test"); }
    }
}
