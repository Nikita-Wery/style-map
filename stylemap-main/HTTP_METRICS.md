# Spring MVC HTTP metrics verification

## Diagnosis (2026-11-07)

The running Minikube backend produced `http_server_requests_seconds_count` and
`http_server_requests_seconds_sum` after requests to its application port 8080,
without a configuration change, rebuild, or restart. Requests used for this
check were GET `/auth/login` (200), `/api/auth/me` (401), and `/api/posts` (401).
The deployed JAR's application configuration and SecurityConfig bytecode matched
the workspace. Its packaged versions are Spring Boot 4.0.3, Spring Framework
7.0.5, and Micrometer 1.16.3.

The reproducible cause of the apparent absence is checking the registry before
application requests have completed. HTTP timers are created on demand for
observed exchanges. Requests to the separate management server on 8081 do not
exercise the application's MVC observation filter. Health probes and Prometheus
scrapes alone leave these HTTP timer series absent, even while JVM, business,
and Spring Security metrics are present. Security's observation family is a
separate instrument and does not prove that the application's HTTP timer has
recorded an exchange.

The regression test reproduces management-only absence, then confirms that
application traffic creates the standard HTTP timer family. This explains the
reported symptom; the earlier scrape's precise traffic history was not retained
and cannot be reconstructed from this check.

Boot 4 moved MVC observation auto-configuration into the `spring-boot-webmvc`
module as `WebMvcObservationAutoConfiguration`. The current dependency graph
already includes this module, `spring-boot-micrometer-observation`, and the
Prometheus registry. Boot registers `webMvcObservationFilter`, a
`FilterRegistrationBean<ServerHttpObservationFilter>`, for REQUEST and ASYNC
dispatches ahead of the security chain. The integration test confirms the
registration is enabled in a real embedded servlet server.

No dependency, property, security-chain, or custom HTTP-counter change is
needed. Adding another filter would risk duplicate instrumentation. Existing
CSRF, authentication, routes, business metrics, and Actuator configuration are
preserved.

## Verify a deployed backend

Forward the existing Deployment in one terminal (no manifest changes):

```sh
kubectl port-forward -n stylemap deployment/backend 18080:8080 18081:8081
```

In another terminal, generate application traffic before reading the scrape:

```sh
curl -sS -o /dev/null -w 'login: %{http_code}\n' http://localhost:18080/auth/login
curl -sS -o /dev/null -w 'API: %{http_code}\n' http://localhost:18080/api/auth/me
curl -fsS http://localhost:18081/actuator/prometheus \
  | rg '^http_server_requests_seconds_(count|sum)\{'
```

For an anonymous client, expect login 200 and API 401. Output should include
count and duration-sum series with `status="200"` and `status="401"`. Counts
are cumulative since application startup. Do not use the frontend's Actuator
path: its SPA fallback can return HTML instead of the backend metrics endpoint.

To verify ingestion, allow at least the configured 15-second scrape interval,
then query VictoriaMetrics:

```promql
http_server_requests_seconds_count{
  job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap"
}
```

```promql
http_server_requests_seconds_sum{
  job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap"
}
```

URI labels are bounded MVC patterns. Security-rejected requests can have
`uri="UNKNOWN"`, so a future alert filtered only to `uri=~"/api/.*"` can omit
some requests. Verify actual labels before selecting an alert scope. A 5xx
series appears only after a 5xx exchange; no production failure endpoint was
added for this verification.

## Automated regression

```sh
mvn -Dtest=HttpServerMetricsTest test
```

The test starts real application and management HTTP servers on ephemeral
ports using Boot auto-configuration, the production SecurityConfig, and the
production exception handler. Database/OAuth services are isolated with test
fixtures; no external database, credentials, or network services are needed.
It checks management-only absence, enabled filter registration, exactly one
recorded request per tested status (200, 500, 401, 302, 403), and both metric
names in the actual management-port Prometheus response. The 500 comes from a
test-only controller exception handled by the production advice; the 403
confirms that POST without CSRF remains rejected.
