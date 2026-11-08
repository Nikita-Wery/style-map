# Проверка финальных Grafana exports

Обновление evidence: семь PNG и `screencast/grafana.mp4` теперь существуют и проверены; изображения просмотрены, video проверено по container metadata. Сведения ниже о первоначальной offline-проверке exports сохранены как история. `export-review.json` не перезаписывался: его пустые screenshot/recording lists относятся к моменту до добавления media. Актуальная media-проверка — `evidence-review.json`, ссылки и наблюдения — в отчёте. Export JSON остаются неизменными.

## Назначение файлов — ничего не удалено

| Файл | Роль |
|---|---|
| `StyleMapApplicationDashboard.json` | Фактический экспорт Application Overview из работающей Grafana, основной файл сдачи |
| `PostgresExporterDashboard.json` | Фактический экспорт PostgreSQL dashboard, основной файл сдачи |
| `stylemap-application-dashboard.json` | Исходная переносимая конфигурация Application Overview для импорта новой копии |
| `postgresql-exporter-dashboard.json` | Исходная переносимая адаптация PostgreSQL 9628 для импорта новой копии |
| `upstream/postgresql-9628-revision-8.json` | Неизменённый публичный исходник, происхождение адаптации; не основной dashboard сдачи |
| `build_dashboards.py` | Генератор исходных переносимых JSON и QUERIES/compatibility; не читает и не изменяет реальные exports |
| `validate.py` | Проверка исходных переносимых JSON; с `--vm-url` перезаписывает исторический `validation-results.json`, поэтому для этой финализации не использовался с этим флагом |
| `exported-metric-names.json` | Исторический inventory имён backend/PostgreSQL metrics, не новая runtime-проверка |
| `validation-results.json` | Предыдущая live-проверка 56 targets от 2026-11-08 10:58:57 UTC; флаг `grafana_import_verified=false` относится к той проверке до экспорта |
| `postgresql-compatibility.json` | Аудит сохранённых/исключённых targets исходного 9628 |
| `QUERIES.md` | Полный перечень панелей/PromQL исходных JSON; применим к exports, поскольку targets совпадают |
| `inspect_exports.py` | Offline-проверка exports, сравнение с исходными JSON, SHA256, inventory media на момент запуска |
| `export-review.json` | Результат этой offline-проверки, не новая проверка Grafana/VM |
| `EXPORT_REVIEW.md` | Назначение файлов, сравнение, ограничения переноса |
| `README.md` | Навигация, перенос и ручная проверка |
| `Отчёт.md` | Основной русский отчёт, разделение конфигурации, исторических наблюдений и непроверенного |
| `screenshots/README.md`, `screenshots/*.png` | Проверенные семь изображений для шести evidence categories |
| `screencast-plan.md`, `screencast/grafana.mp4` | План/статус записи и MP4 5:24.533; playback/звук отдельно не проверены |
| `inspect_evidence.py`, `evidence-review.json` | PNG decoding, MP4 metadata и hashes; media не изменяются |
| `FINAL_CONTENTS.md` | Рекомендуемый состав архива, список лишних intermediates и оставшиеся проверки |

Все исходные/экспортированные dashboard JSON и предыдущие результаты проверки сохранены без изменений. Документация обновлена после определения назначения файлов. ZIP не создавался.

## Сравнение

| Свойство | Application | PostgreSQL |
|---|---|---|
| Название | StyleMap - Application Overview | StyleMap - PostgreSQL Exporter (9628 adapted) |
| Содержательные панели / строки | 19 / 4 | 27 / 1 |
| PromQL targets | 26 | 30 |
| Grafana dashboard ID | 2 | 3 |
| Grafana UID | `00cd27cc-d028-4860-8189-ce1da7618909` | `2c735229-ab8d-4fdd-b6ea-44982358ee35` |
| schemaVersion | 42 (исходная: 39) | 42 (исходная: 39) |
| pluginVersion в export | 12.2.0 | 12.2.0 |

Все targets (включая expr, legend, interval, instant/range и datasource), panel IDs, titles, descriptions, grid positions и units совпадают с соответствующим сгенерированным JSON. Период отображения `now-1h..now`, refresh 15s. Grafana добавила стандартные visual defaults, mappings, built-in annotations, dashboard metadata и выбранное значение datasource variable. В Application для 5xx percentage и двух SLA stats сохранены добавленные в Grafana min=0/max=100. Базовое значение threshold steps нормализовано из null в 0. Эти реальные настройки не откатывались к generator defaults.

## Datasource и переносимость

Панели и targets используют `{ "type": "prometheus", "uid": "${DS_PROMETHEUS}" }`. `DS_PROMETHEUS` — datasource variable с query=`prometheus`; в обоих exports сохранён выбранный source с UI-именем **prometheus**, UID **ag3o6jjh0lb7kb**. Это идентификатор, не секрет. Такой тип datasource подходит для Prometheus API VictoriaMetrics. JSON dashboard **не содержит самого datasource definition, URL или результата Save & test**: фактический backend URL подтвердить в UI существующего datasource, не создавать новый.

PostgreSQL variable `database`: multi-select, All=`.*`, query=`label_values(pg_stat_database_numbackends{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"}, datname)`, ссылки на тот же datasource. В panel expressions используется `${database:regex}`. Grafana раскрывает `$__rate_interval`; targets задают 15s min step. Built-in Annotations & Alerts с `-- Grafana --` — внутренний datasource Grafana, не отдельная база HTTP/PostgreSQL metrics.

Exports привязаны к локальным dashboard IDs/UIDs и содержат сохранённый выбор datasource. Это точные рабочие snapshots, **не полностью обезличенные import templates**. В другом Grafana UID выбранного source может отсутствовать: выбрать свой существующий Prometheus-compatible VictoriaMetrics в variable. Не импортировать raw exports поверх существующих dashboards. Для новой копии предпочтительны сохранённые переносимые файлы с null id/uid; они сохраняют те же запросы, но не все Grafana visual defaults. Если нужны именно defaults exports, импортировать отдельную копию с новым UID через UI/локальную копию, не изменяя оригиналы и не подтверждая overwrite.

## Границы проверки

Проверка exports — offline: strict JSON (включая duplicate keys), IDs, datasource references, переменные, имена metric families по сохранённому inventory, совпадение с 56 ранее успешно проверенными queries. Она не является новым исполнением PromQL или повторным измерением SLA. Добавленные screenshots визуально подтверждают показанные панели, выбор source prometheus, database stylemap и PostgreSQL Query Inspector с 241 rows. URL datasource и режим All не показаны. Dashboard JSON не содержит результатов; SLA screenshot показывает 98.9%/1.67%, тогда как 1.57% — более раннее сообщённое наблюдение.

Повторить из корня проекта без изменений файлов:

```sh
python3 submission-grafana/inspect_exports.py
python3 submission-grafana/validate.py
git diff --check -- submission-grafana
```
