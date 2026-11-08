# Реальные screenshots — проверено

Все шесть категорий evidence представлены семью PNG. Проверены формат, структура и полное декодирование pixels; каждое изображение просмотрено. Файлы не изменялись.

| Файл | Проверенное содержание |
|---|---|
| [01-application-health.png](01-application-health.png) | Source prometheus, Last 1 hour, health/readiness=1, RPS=0, 5xx=Нет данных, status/errors graphs |
| [02-red-latency.png](02-red-latency.png) | Mean 12.8ms, p95 15.2ms, p99 15.4ms (Last*), rate by URI |
| [03-jvm-use.png](03-jvm-use.png) | Heap, utilization, process/system CPU, threads; GC/Hikari values обрезаны нижним краем |
| [04-readiness-sla.png](04-readiness-sla.png) | Target ≥99%, 30 days; availability 98.9%, coverage 1.67% — не подтверждение полного SLA |
| [05-1-postgresql.png](05-1-postgresql.png) | Source prometheus, Database=stylemap, pg_up=1, fetch/insert/update |
| [05-2-postgresql.png](05-2-postgresql.png) | Database=stylemap, sessions, commits/rollbacks, update/fetch |
| [06-query-inspector.png](06-query-inspector.png) | Current fetch data: 241 rows, Step 15s, resolved stylemap selector, response/results |

Значения описывают screenshots, не новые runtime-запросы. Прямой VictoriaMetrics URL и переключение All на кадрах не показаны. Дополнительный кадр GC/HikariCP полезен для полного статического evidence USE, но все шесть обязательных категорий уже есть. Изображения включены в [отчёт](../Отчёт.md). Размеры и hashes — `../evidence-review.json`.
