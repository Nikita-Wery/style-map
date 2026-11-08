# Панели и точные PromQL выражения

Проверка финальных exports: все targets в `StyleMapApplicationDashboard.json` и `PostgresExporterDashboard.json` совпадают с описанными ниже generated конфигурациями. Этот список отражает конфигурацию, а не новые измеренные значения. См. `EXPORT_REVIEW.md`.

Grafana раскрывает `$__rate_interval`; минимум 60s при scrape 15s. DS_PROMETHEUS — существующий Prometheus-compatible VictoriaMetrics datasource.

## StyleMap - Application Overview

### Backend scrape health

0: хотя бы один обнаруженный target недоступен; 1: все обнаруженные UP. Исчезнувший target не учитывается; пусто — неизвестно.

```promql
min(up{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"})
```

### Blackbox readiness

Readiness backend, включая db; не frontend/Ingress. Нет данных не равно UP.

```promql
probe_success{job="stylemap-readiness",instance="http://backend-management.stylemap.svc.cluster.local:8081/actuator/health/readiness"}
```

### HTTP RPS

Скорость завершённых запросов; пусто — нет серии, 0 — наблюдаемый нулевой трафик.

```promql
sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval]))
```

### HTTP 5xx percentage

Нет трафика: Нет данных. Если общий counter существует, отсутствие 5xx-серии означает отсутствие записанных 5xx. Пороги 1/5% — визуальные, не SLA.

```promql
100 * (sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods",status=~"5.."}[$__rate_interval])) or (0 * sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval])))) / (sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval])) > 0)
```

### Request rate by status

Traffic и errors; все статусы, включая отклонённые Security запросы. Легенда — HTTP status.

```promql
sum by (status) (rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval]))
```

### HTTP errors: 4xx / 5xx

Ошибки в запросах/с; fallback 0 применяется только при наблюдаемом общем трафике, не при отсутствии метрик.

```promql
(sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods",status=~"4.."}[$__rate_interval])) or (0 * sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval]))))
```

```promql
(sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods",status=~"5.."}[$__rate_interval])) or (0 * sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval]))))
```

### Average HTTP latency

Взвешенное среднее: rate(sum)/rate(count). При нулевом трафике нет значения.

```promql
sum(rate(http_server_requests_seconds_sum{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval])) / (sum(rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval])) > 0)
```

### HTTP latency p95 / p99

Приближённые квантили по объединённым histogram buckets всех backend pod/status/URI. le сохраняется; при отсутствии наблюдений NaN.

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval])))
```

```promql
histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval])))
```

### Top request rates by URI

MVC route templates. UNKNOWN включает запросы, отклонённые до MVC; они не исключаются.

```promql
topk(10, sum by (uri) (rate(http_server_requests_seconds_count{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval])))
```

### JVM heap used / maximum

Сумма heap pools по pod; max отображается только для JVM с определённым лимитом.

```promql
sum by (kubernetes_pod_name) (jvm_memory_used_bytes{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods",area="heap"})
```

```promql
sum by (kubernetes_pod_name) (jvm_memory_max_bytes{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods",area="heap"})
```

### JVM heap utilization

Heap utilization по pod; max<=0 даёт Нет данных. Визуальные пороги, не SLO.

```promql
100 * sum by (kubernetes_pod_name) (jvm_memory_used_bytes{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods",area="heap"}) / (sum by (kubernetes_pod_name) (jvm_memory_max_bytes{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods",area="heap"}) > 0)
```

### CPU utilization: process / system

Micrometer gauges 0..1: не применять rate(). System CPU — доступное JVM окружение, не гарантированно весь Kubernetes node.

```promql
process_cpu_usage{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}
```

```promql
system_cpu_usage{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}
```

### JVM live threads

Число живых JVM threads по pod; контекст runtime saturation.

```promql
jvm_threads_live_threads{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}
```

### GC pause time / second

Секунды stop-the-world пауз за секунду; rate counter длительности, не средняя пауза.

```promql
sum by (kubernetes_pod_name) (rate(jvm_gc_pause_seconds_sum{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval]))
```

### HikariCP connections

Active/idle/max — utilization и capacity; pending — saturation, очередь ожидания подключения.

```promql
hikaricp_connections_active{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}
```

```promql
hikaricp_connections_idle{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}
```

```promql
hikaricp_connections_pending{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}
```

```promql
hikaricp_connections_max{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}
```

### HikariCP pool utilization

Active/max по pool и pod. Нулевой max не маскируется.

```promql
100 * hikaricp_connections_active{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"} / (hikaricp_connections_max{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"} > 0)
```

### HikariCP connection timeout rate

USE errors: подключения, не полученные до timeout, за секунду.

```promql
sum by (pool, kubernetes_pod_name) (rate(hikaricp_connections_timeout_total{app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"}[$__rate_interval]))
```

### Observed readiness availability — 30d

Цель ≥99%, период 30 суток из submission-slo/Отчёт_SLI_SLO_SLA.md. Только имеющиеся probe samples; неполная история не подтверждает SLA. Это readiness-компонент, не общий SLA и не договорная гарантия.

```promql
100 * avg_over_time(probe_success{job="stylemap-readiness",instance="http://backend-management.stylemap.svc.cluster.local:8081/actuator/health/readiness"}[30d])
```

### Readiness sample coverage — 30d

30*24*60*60/15 = 172800 плановых проб. При <100% SLA не подтверждён. Дубликаты/смену scrape interval проверять отдельно; >100% — проверить ingestion.

```promql
100 * count_over_time(probe_success{job="stylemap-readiness",instance="http://backend-management.stylemap.svc.cluster.local:8081/actuator/health/readiness"}[30d]) / 172800
```

## StyleMap - PostgreSQL Exporter (9628 adapted)

### PostgreSQL connection health

pg_up — связь exporter с БД; не только scrape health.

```promql
pg_up{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Current fetch data

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
sum(rate(pg_stat_database_tup_fetched{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval]))
```

### Current insert data

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
sum(rate(pg_stat_database_tup_inserted{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval]))
```

### Current update data

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
sum(rate(pg_stat_database_tup_updated{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval]))
```

### Max Connections

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_max_connections{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Shared Buffers

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_shared_buffers_bytes{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Effective Cache

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_effective_cache_size_bytes{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Maintenance Work Mem

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_maintenance_work_mem_bytes{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Work Mem

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_work_mem_bytes{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Max WAL Size

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_max_wal_size_bytes{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Random Page Cost

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_random_page_cost{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Seq Page Cost

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_seq_page_cost{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Max Worker Processes

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_max_worker_processes{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Max Parallel Workers

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_settings_max_parallel_workers{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}
```

### Active sessions

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_stat_activity_count{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", state="active"}
```

### Transactions

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_xact_commit{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"}[$__rate_interval])
```

```promql
rate(pg_stat_database_xact_rollback{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"}[$__rate_interval])
```

### Update data

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_tup_updated{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval])
```

### Fetch data (SELECT)

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_tup_fetched{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval])
```

### Insert data

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_tup_inserted{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval])
```

### Lock tables

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_locks_count{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", mode=~".*"}
```

### Return data

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_tup_returned{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval])
```

### Idle sessions

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_stat_activity_count{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", state=~"idle|idle in transaction|idle in transaction (aborted)"}
```

### Delete data

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_tup_deleted{datname=~"${database:regex}", kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval])
```

### Cache Hit Rate

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
pg_stat_database_blks_hit{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"} / (pg_stat_database_blks_read{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"} + pg_stat_database_blks_hit{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"})
```

### Buffers (bgwriter)

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_bgwriter_buffers_alloc_total{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval])
```

```promql
rate(pg_stat_bgwriter_buffers_clean_total{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}[$__rate_interval])
```

### Conflicts/Deadlocks

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_conflicts{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"}[$__rate_interval])
```

```promql
rate(pg_stat_database_deadlocks{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"}[$__rate_interval])
```

### Temp File (Bytes)

Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.

```promql
rate(pg_stat_database_temp_bytes{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods", datname=~"${database:regex}"}[$__rate_interval])
```
