# StyleMap — Grafana Dashboard homework

Основные файлы сдачи: **`StyleMapApplicationDashboard.json`** и **`PostgresExporterDashboard.json`**, экспортированные из Grafana и сохранённые без изменений. Все шесть категорий evidence представлены семью проверенными PNG в `screenshots/`; запись — [screencast/grafana.mp4](screencast/grafana.mp4), 5:24.533, 250150863 bytes. Контейнер/metadata видео проверены; воспроизведение и звук нужно просмотреть локально. Отчёт содержит реальные images и ограничения проверки. Kubernetes/application/deployed Grafana не изменялись. ZIP не создавался. Рекомендуемый состав архива — [FINAL_CONTENTS.md](FINAL_CONTENTS.md).

## Состав

- `StyleMapApplicationDashboard.json`, `PostgresExporterDashboard.json` — фактические Grafana exports, а не generated templates.
- `EXPORT_REVIEW.md`, `export-review.json`, `inspect_exports.py` — полный inventory назначения файлов, сравнение, SHA256 и повторяемая offline-проверка exports.
- `stylemap-application-dashboard.json` — Application Overview, RED / USE / Golden Signals и отдельный readiness-компонент SLA.
- `postgresql-exporter-dashboard.json` — адаптация публичного PostgreSQL Database 9628, revision 8 для текущего sidecar.
- `upstream/postgresql-9628-revision-8.json` — неизменённый исходный JSON из каталога; **для импорта используйте адаптацию**, не этот файл.
- `QUERIES.md` — полный перечень панелей, описаний и точных PromQL обоих dashboards.
- `postgresql-compatibility.json` — перечень сохранённых/исключённых upstream панелей.
- `exported-metric-names.json` — имена метрик из фактических backend/exporter scrape, без значений и credentials.
- `validation-results.json` — результаты read-only запросов к VictoriaMetrics на дату проверки, не доказательство импорта в Grafana.
- `Отчёт.md`, `screencast-plan.md`, `screenshots/README.md` — материалы для завершения сдачи.
- `screenshots/*.png` — семь реальных изображений, включая два кадра PostgreSQL dashboard.
- `screencast/grafana.mp4` — готовая запись; файл попадает под repository ignore `*.mp4`, поэтому не полагаться на git archive/список tracked files при упаковке.
- `inspect_evidence.py`, `evidence-review.json` — PNG decoding, MP4 metadata, media hashes и повторная проверка исходных export hashes. PNG script требует Pillow.

## Импорт без перезаписи

**Для текущей Grafana повторный импорт не нужен:** exports уже предоставлены пользователем. Следующие шаги — только для воспроизведения в новой папке/другой Grafana. Не импортировать actual exports поверх имеющихся UID. Для той же query configuration использовать исходные переносимые JSON ниже. Они не содержат всех добавленных Grafana visual defaults; рабочие exports остаются первичными артефактами сдачи. Подробнее — EXPORT_REVIEW.md.

1. В корне проекта откройте отдельный терминал:

   ```sh
   kubectl --context=minikube -n stylemap port-forward svc/grafana 13000:3000
   ```

2. Откройте `http://localhost:13000`, войдите существующей учётной записью. Пароли не копировать в отчёт/JSON/запись.
3. В Connections → Data sources найдите **существующий** Prometheus-compatible VictoriaMetrics datasource. Не создавайте новый и не меняйте настройки существующего. Targets задают Min step 15s согласно scrape interval; для запросов в JSON задан datasource variable `DS_PROMETHEUS`, а не жёсткий UID.
4. Создайте новую папку, например `StyleMap Grafana Homework 2026-11-08` (при совпадении выберите другое имя).
5. Dashboards → New → Import → Upload JSON file → `stylemap-application-dashboard.json`. Выберите новую папку, проверьте имя `StyleMap - Application Overview`, затем Import. `id` и `uid` в файле равны null, Grafana выделит новые. Если UI предлагает overwrite — отмените и выберите новое имя/UID; существующие dashboards не перезаписывать.
6. В верхнем списке `VictoriaMetrics datasource` выберите уже работающий datasource. При нескольких Prometheus sources проверьте выбранный URL в настройках существующего источника. Сохраните dashboard в новой папке.
7. Повторите импорт для `postgresql-exporter-dashboard.json`; название `StyleMap - PostgreSQL Exporter (9628 adapted)`. Выберите тот же datasource. Database=`All` или `stylemap`.
8. Application: диапазон Last 1 hour, refresh 15s. SLA имеет фиксированный lookback `[30d]` вне зависимости от выбранного диапазона. В Query inspector подтвердите отсутствие ошибок, раскрытие `$__rate_interval` минимум в 60s при 15s scrape. При неверной настройке datasource временно используйте 5m в редакторе копии панели; не меняйте существующие dashboards.
9. Сверьте панели с Explore и `QUERIES.md`. Проверьте единицы seconds / bytes / percent, legends и читаемость. Actual exports уже сохранены под именами `StyleMapApplicationDashboard.json` и `PostgresExporterDashboard.json`; не регенерировать и не заменять их generated templates. При необходимости нового экспорта сохранить отдельный файл после определения его назначения.

## Трафик и проверка

Откройте ещё два терминала:

```sh
kubectl --context=minikube -n stylemap port-forward deployment/backend 18080:8080 18081:8081
kubectl --context=minikube -n monitoring port-forward svc/vm-single-victoria-metrics-single-server 18428:8428
```

В другом терминале создайте безопасный GET-трафик, не POST-публикации:

```sh
for i in $(seq 1 20); do
  curl -sS -o /dev/null http://localhost:18080/auth/login
  curl -sS -o /dev/null http://localhost:18080/api/auth/me
  sleep 1
done
curl -fsS http://localhost:18081/actuator/prometheus \
  | rg '^http_server_requests_seconds_(count|sum|bucket)\{'
curl -fsSG http://localhost:18428/api/v1/query \
  --data-urlencode 'query=pg_up{kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0"}'
python3 submission-grafana/validate.py --vm-url http://localhost:18428
```

Подождите минимум два scrape-интервала (30s), обновите Grafana. Anonymous `/api/auth/me` обычно даёт 401, `/auth/login` — 200. Искусственно вызывать 500 в production не нужно; нет 5xx-серии до первого 5xx — нормально. После прекращения трафика RPS может быть 0, а latency/error percentage — «Нет данных». Отсутствие метрик не подменяется успешными запросами.

Офлайн-проверка (Python 3.12+, без сторонних пакетов; ничего не перезаписывает):

```sh
python3 submission-grafana/inspect_exports.py
python3 submission-grafana/validate.py
python3 submission-grafana/inspect_evidence.py
git diff --check -- submission-grafana
```

`build_dashboards.py` нужен только для осознанной повторной генерации исходных templates: он перезаписывает generated JSON, QUERIES.md и compatibility audit, но не actual exports. Для сдачи запускать его не требуется. Live-проверка `validate.py --vm-url ...` обновляет исторический `validation-results.json`, выполняет GET `/api/v1/query`, не пишет в Grafana/кластер. Сохраните старый результат перед намеренным новым измерением; в данной финализации этот флаг не использовался. Она проверяет исходные templates, чьи targets совпадают с exports; не заменяет rendering test. В историческом результате `$__rate_interval` раскрыт в `5m`, database — `.*`.

## Что осталось перед сдачей

Обязательные screenshots существуют и просмотрены, включая PostgreSQL Query Inspector: 241 rows, Step 15s, selector stylemap; datasource `prometheus` виден на dashboards. Прямой datasource URL и переключение Database=All на кадрах не показаны — отдельная проверка не заявляется. Screenshot SLA показывает 98.9% sampled readiness / **1.67%** coverage; прежние **1.57%** остаются отдельным историческим наблюдением. Полный 30-дневный SLA не подтверждён.

Перед отправкой просмотреть video в локальном плеере (начало/середину/конец, звук и отсутствие секретов); metadata не заменяет playback. Дополнительный screenshot нижнего JVM раздела с GC/HikariCP полезен, поскольку текущий crop их значения обрезает. При упаковке явно включить ignored `screencast/grafana.mp4`. Если отправка только через Git — отдельно решить доставку видео, не менять .gitignore автоматически. Архив пока не создавать.

## PostgreSQL: происхождение и совместимость

Исходник: [Grafana PostgreSQL Database 9628](https://grafana.com/grafana/dashboards/9628-postgresql-database/), revision 8, [точный download URL](https://grafana.com/api/dashboards/9628/revisions/8/download). Авторство исходного dashboard сохраняется в `upstream` JSON; наша копия явно обозначена adapted. Каталог ссылается на lstn/misc-grafana-dashboards. Совместимость проверена по текущему prometheus-community/postgres-exporter v0.20.1 и PostgreSQL sidecar, а не заявлена для любых версий/наборов collectors.

Оригинал нельзя импортировать без адаптации: Helm label `release` отсутствует; namespace/release variables построены на старой exporter metric; `pg_static` и `pg_postmaster_start_time_seconds` отсутствуют; часть старых `pg_stat_bgwriter_*` метрик отсутствует. Адаптация использует scope `job="kubernetes-pods",kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0"`, динамическую database variable и тот же datasource variable. При переименовании StatefulSet scope надо обновить в копии JSON.

Сохранены поддерживаемые настройки PostgreSQL, tuples, sessions, transactions, locks, cache hits, bgwriter buffers, conflicts/deadlocks и temp bytes. Неподдерживаемые series исключены (в buffers сохранены только существующие targets). Counter tuples/transactions используют rate; настройки — gauges. CPU/memory/fd процесса exporter удалены: это ресурсы exporter, а не PostgreSQL. Старые `graph`/`singlestat` заменены встроенными `timeseries`/`stat`; дополнительных Grafana plugins не требуется. Новая панель `pg_up` показывает связь exporter с БД. Полный аудит — `postgresql-compatibility.json`.

Не устанавливать другой exporter, не менять collectors, SQL extensions или PostgreSQL config ради пустых legacy panels.
