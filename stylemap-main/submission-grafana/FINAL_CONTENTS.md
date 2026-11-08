# Состав будущего архива — архив пока не создан

## Основные deliverables: включить

- `StyleMapApplicationDashboard.json`
- `PostgresExporterDashboard.json`
- `Отчёт.md`
- `README.md`
- `QUERIES.md`
- `screenshots/README.md` и **все семь существующих PNG**: `01-application-health.png`, `02-red-latency.png`, `03-jvm-use.png`, `04-readiness-sla.png`, `05-1-postgresql.png`, `05-2-postgresql.png`, `06-query-inspector.png`.
- `screencast/grafana.mp4` — 250150863 bytes; включать явно, так как Git игнорирует `*.mp4`.
- `screencast-plan.md` — фактический статус записи и границы проверки.

## Полезные подтверждения: включить рядом

- `evidence-review.json` — PNG/video metadata и SHA256.
- `EXPORT_REVIEW.md`, `export-review.json` — сравнение exports/исходников и ограничения переноса. Исторический export-review.json предшествует добавлению media.
- `validation-results.json` — историческая успешная VM-проверка 56 запросов; не новые измерения.
- `postgresql-compatibility.json` — происхождение и совместимость 9628 adapted.
- `FINAL_CONTENTS.md` — этот список.

## Промежуточные файлы: оставить в репозитории, обычно не включать в сдачу

Назначение установлено; **ничего из этого не удалено**:

- `stylemap-application-dashboard.json`, `postgresql-exporter-dashboard.json` — generated import templates, дублируют query configuration реальных exports; сохраняются для воспроизведения, но не выдаются за финальные exports.
- `upstream/postgresql-9628-revision-8.json` — скачанный исходник каталога; ссылка/атрибуция уже есть в README. Не рабочая конфигурация текущего sidecar.
- `build_dashboards.py` — generator промежуточных templates/QUERIES/compatibility; для просмотра сдачи не нужен.
- `exported-metric-names.json`, `validate.py`, `inspect_exports.py`, `inspect_evidence.py` — toolkit повторной repository-проверки. Скрипты требуют вспомогательные файлы/среду, а inspect_evidence.py — Pillow. При желании приложить toolkit отдельно целиком с templates/inventory; минимальному архиву нужны только результаты.

README содержит разделы воспроизведения из полного репозитория; команды toolkit/generated imports не применимы к минимальному архиву без этих промежуточных файлов. Для импорта финальных exports выбирать новую копию/new UID и свой datasource, не перезаписывать existing dashboards. Цели предыдущего SLO-отчёта изложены в основном отчёте; внешний файл для минимального архива не требуется.

## Остаточные проверки перед отправкой

1. Локально воспроизвести начало/середину/конец MP4 и проверить звук/отсутствие секретов. Здесь проверены контейнер и metadata, не весь видеопоток.
2. Дополнительно можно снять GC/HikariCP: текущий JVM crop не показывает их значения. Отсутствующих обязательных категорий screenshots нет.
3. Не объявлять полный 30-дневный SLA выполненным: screenshot 98.9% availability / 1.67% coverage; прежние 1.57% — исторический контекст.
4. При будущей упаковке проверить включение ignored MP4 и сохранение структуры `screenshots/` / `screencast/`, чтобы ссылки отчёта работали.
