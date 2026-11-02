# StyleMap: SLI, SLO и SLA

## Статус и состав

Конфигурация развёрнута и проверена в работающем кластере. В VMUI подтверждены `probe_success{job="stylemap-readiness"}=1`, `pg_up=1`, backend scrape `up{app="backend"}=1` и наличие publication Timer в `/actuator/prometheus`. HTTP request counter в live scrape отсутствует, поэтому HTTP success SLI не используется и отдельная HTTP-метрика не добавлялась. Для сдачи записан скринкаст с демонстрацией нормального состояния, управляемой деградации публикации и нарушения SLO/SLA. Основные файлы конфигурации находятся в `k8s/monitoring/`: `backend-management.yaml`, `blackbox-exporter.yaml`, `guide-vmsingle-values.yaml`. Демо-настройка находится в `k8s/01-configmap.yaml`, `src/main/resources/application.yaml` и `PostService.java`.

SLI — измеренный показатель качества. SLO — целевое значение показателя за заданный период. SLA — соглашение об условиях качества и последствиях нарушения. Это учебное соглашение, не коммерческий договор.

## Показатели и цели

Требование «не менее трёх существующих метрик» выполнено следующими метриками предыдущего задания:

1. `pg_up`.
2. `stylemap_outfits_publish_duration_seconds_sum`.
3. `stylemap_outfits_publish_duration_seconds_count`.

Последние две метрики вместе образуют один SLI задержки публикации: это три существующие метрики, а не три независимых семейства. `probe_success` — **новая метрика этого задания**, полученная Blackbox Exporter; она не засчитывается в три метрики предыдущей работы. Итоговый набор содержит три SLI:

| SLI | Источник | Определение | SLO |
|---|---|---|---|
| PostgreSQL availability | `pg_up` | Хорошие интервалы / все плановые интервалы | ≥99% |
| Publication latency compliance | `stylemap_outfits_publish_duration_seconds_sum`, `_count` | Хорошие интервалы со средним временем успешной публикации ≤1 с / интервалы с публикациями | ≥95% |
| Backend readiness availability | `probe_success` | Успешные проверки / плановые проверки | ≥99% |

Реальный иллюстративный отчётный период — 30 суток. Проверки/оценки выполняются каждые 15 секунд. Для задержки каждая оценка использует rolling mean за предыдущие 2 минуты; это средняя длительность успешной сервисной операции, не p95 и не доля отдельных быстрых публикаций. В демонстрации сам отчётный период сокращён до 2 минут, чтобы деградация и восстановление были видны. Значения демонстрации не доказывают выполнение 30-дневных целей.

Общий SLA выполнен только при выполнении всех трёх обязательных SLO за период при достаточных данных. Обязательные составляющие SLA: доступность PostgreSQL ≥99%, latency compliance публикаций ≥95% и доступность backend readiness ≥99%. При нарушении: оформить учебный инцидент, установить причину, исправить конфигурацию/код и повторить измерение с приложенными графиками. Денежные компенсации не предусмотрены. Недостаточные данные означают «SLA не подтверждён», а не успех.

## Сбор и probe

Spring management работает на 8081, приложение — на 8080. Отдельный ClusterIP `backend-management` не меняет application Service. Blackbox exporter в namespace `stylemap` выполняет GET:

`http://backend-management.stylemap.svc.cluster.local:8081/actuator/health/readiness`

Модуль `stylemap_readiness` требует HTTP 200, запрещает переходы по redirect и проверяет тело регулярным выражением `"status"\s*:\s*"UP"`. Timeout probe — 3 с, scrape timeout — 5 с. Job `stylemap-readiness` собирает `/probe` каждые 15 с. `instance` равен постоянному URL цели. Старый job pod discovery не изменён. Метрика `up{job="stylemap-readiness"}` означает доступность exporter; `probe_success` — результат проверки backend.

Readiness включает `db`, поэтому отказ БД одновременно ухудшает `pg_up` и readiness. Это внутренняя проверка backend, а не доступность frontend/Ingress для пользователя. Через frontend проверять `/actuator/...` нельзя: nginx может вернуть SPA HTML с HTTP 200.

## VMUI: воспроизводимые графики

Для доступа к развёрнутой VictoriaMetrics открыть port-forward:

```sh
kubectl port-forward -n monitoring svc/vm-single-victoria-metrics-single-server 8428:8428
```

Открыть `http://localhost:8428/vmui`, выбрать диапазон 15–30 минут, шаг 15 с и Graph. Добавлять показатель и соответствующую константу отдельными запросами. Все доли ниже заданы от 0 до 1. Константы: `vector(0.99)`, `vector(0.95)`, `vector(1)`; последняя — порог задержки в секундах или целевое значение булевого SLA.

### PostgreSQL

Для единственной существующей реплики график доступности по полученным отсчётам:

```promql
avg_over_time(pg_up{job="kubernetes-pods",app="postgres",kubernetes_namespace="stylemap"}[2m])
```

Цель: `vector(0.99)`. `pg_up` бинарная; среднее равно доле успешных отсчётов при регулярном сборе. Сырые значения показывать отдельным запросом `pg_up{app="postgres",kubernetes_namespace="stylemap"}`.

### Публикации

Средняя длительность успешной операции:

```promql
sum(rate(stylemap_outfits_publish_duration_seconds_sum{app="backend",kubernetes_namespace="stylemap"}[2m]))
/
sum(rate(stylemap_outfits_publish_duration_seconds_count{app="backend",kubernetes_namespace="stylemap"}[2m]))
```

Порог: `vector(1)`. Время включает сервисную работу и завершение успешной транзакции; загрузка HTTP multipart до входа в сервис и неуспешные операции не измеряются этим Timer. Buckets и percentile отсутствуют.

Доля хороших оценок задержки за демонстрационные 2 минуты (шаг 15 с):

```metricsql
WITH (
  n = sum(rate(stylemap_outfits_publish_duration_seconds_count{app="backend",kubernetes_namespace="stylemap"}[2m])),
  m = sum(rate(stylemap_outfits_publish_duration_seconds_sum{app="backend",kubernetes_namespace="stylemap"}[2m])) / n
)
avg_over_time(((m <= bool 1) and (n > 0))[2m:15s])
```

Цель: `vector(0.95)`. Нулевой трафик исключён из оценок; пропуски требуют отдельной проверки покрытия. Для 30 суток заменить только внешний `[2m:15s]` на `[30d:15s]`; внутренний rolling mean остаётся двухминутным. Запрос за 30 суток может быть дорогим; для постоянной эксплуатации нужны recording rules/предвычисление, здесь они не добавлены.

### Readiness

```promql
probe_success{job="stylemap-readiness"}
```

```promql
avg_over_time(probe_success{job="stylemap-readiness"}[2m])
```

Цель доступности: `vector(0.99)`. Для 30 суток использовать `[30d]` и проверить полноту данных.

### Трафик и покрытие данных

```promql
sum(rate(stylemap_outfits_publish_duration_seconds_count{app="backend",kubernetes_namespace="stylemap"}[2m]))
```

```promql
up{job="kubernetes-pods",kubernetes_namespace="stylemap",app=~"backend|postgres"}
```

```promql
up{job="stylemap-readiness"}
```

```promql
count_over_time(pg_up{app="postgres",kubernetes_namespace="stylemap"}[2m])
```

```promql
count_over_time(probe_success{job="stylemap-readiness"}[2m])
```

При 15-секундном сборе ожидаются 8 отсчётов за 2 минуты. Проверять также свежесть: `time() - timestamp(pg_up{app="postgres",kubernetes_namespace="stylemap"})` и аналогично для `probe_success`; нормальное значение <30 с. Рестарты меняют Pod labels; нельзя суммировать средние разных Pod без выравнивания по времени.

Для реального периода знаменатель availability — все плановые проверки. Потерянные проверки учитывать как неизвестные, отдельно показывать покрытие; SLO/SLA не подтверждать при неполной истории. `avg_over_time` сам по себе усредняет только существующие отсчёты. Ни `or vector(1)`, ни подмена отсутствия успешным значением не допускаются.

### Общий SLA, демонстрация

Следующий MetricQL вычисляет 0/1 для трёх обязательных SLO: PostgreSQL, публикации и readiness. Предполагает одну PostgreSQL-реплику, одну probe-цель и регулярный сбор. Фильтры покрытия делают результат неизвестным при обнаруженных пропусках; отдельно проверять targets и графики покрытия: subquery может использовать последнее значение внутри lookback, поэтому это не аудит непрерывности за 30 суток.

```metricsql
WITH (
  db = pg_up{job="kubernetes-pods",app="postgres",kubernetes_namespace="stylemap"},
  d = min(avg_over_time(db[2m])),
  probe = probe_success{job="stylemap-readiness"},
  r = min(avg_over_time(probe[2m])),
  probe_coverage = min(count_over_time(probe[2m])) >= 8,
  probe_fresh = max(time() - timestamp(probe)) < 30,
  probe_scrape = min(min_over_time(up{job="stylemap-readiness"}[2m])) == 1,
  n = sum(rate(stylemap_outfits_publish_duration_seconds_count{app="backend",kubernetes_namespace="stylemap"}[2m])),
  m = sum(rate(stylemap_outfits_publish_duration_seconds_sum{app="backend",kubernetes_namespace="stylemap"}[2m])) / n,
  l = avg_over_time(((m <= bool 1) and (n > 0))[2m:15s]),
  coverage = min(count_over_time(db[2m])) >= 8,
  latency_coverage = count_over_time(((m <= bool 1) and (n > 0))[2m:15s]) >= 8,
  scrape_coverage = min(count_over_time(up{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap"}[2m])) >= 8,
  scrape_success = min(min_over_time(up{job="kubernetes-pods",app=~"backend|postgres",kubernetes_namespace="stylemap"}[2m])) == 1,
  fresh = max(time() - timestamp(db)) < 30,
  backend_scrape = min(up{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap"}) == 1
)
((d >= bool 0.99) * (l >= bool 0.95) * (r >= bool 0.99))
and (n > 0) and coverage and latency_coverage
and scrape_coverage and scrape_success and fresh and backend_scrape
and probe_coverage and probe_fresh and probe_scrape
```

Цель: `vector(1)`. Дополнительно проверить `count_over_time` backend scrape, отсутствие дыр и полную историю latency-оценок. Пустой график/NaN означает недостаточные данные. Для настоящего SLA рассчитывать доступность PostgreSQL и readiness по плановым проверкам за весь период и latency compliance по всем допустимым оценкам; итог — логическое И трёх результатов. Не выдавать этот короткий demo query за готовый 30-дневный SLA.

## Демо-задержка

`stylemap.demo.publish-delay-ms` получает `${STYLEMAP_DEMO_PUBLISH_DELAY_MS:0}`. Значение ограничивается диапазоном 0..5000 мс (отрицательное становится 0, больше 5000 — 5000). По умолчанию sleep не вызывается. Задержка стоит после старта существующего Timer внутри транзакционного метода; успешные метрики по-прежнему записываются только после успешного commit. При interruption восстанавливается флаг потока и выбрасывается runtime exception: транзакция откатывается без записи успеха. Настройка нужна только для курсовой демонстрации; изменение env требует перезапуска backend.

## Воспроизведение развёртывания и проверки

Live-работоспособность перечисленных выше метрик и probe подтверждена. Ниже приведены команды, которыми конфигурация воспроизводится в Minikube. Backend image с новым кодом используется под тегом `stylemap-backend:k8s-v1` с `imagePullPolicy: Never`.

```sh
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/monitoring/backend-management.yaml
kubectl apply -f k8s/monitoring/blackbox-exporter.yaml
helm upgrade vm-single vm/victoria-metrics-single -n monitoring --version 0.49.0 -f k8s/monitoring/guide-vmsingle-values.yaml
kubectl rollout restart deployment/backend -n stylemap
kubectl rollout status deployment/backend -n stylemap
```

Перед Helm upgrade необходимо сохранять фактические несекретные настройки хранения/retention; для реального 30-дневного периода нужна история не менее 30 суток. В работающем кластере проверены загрузка конфигурации exporter, `probe_success=1`, readiness HTTP 200/UP, `pg_up=1` и обе метрики publication Timer. Для диагностики exporter при необходимости можно port-forward его Service на 9115 и вызвать `/probe?module=stylemap_readiness&target=http%3A%2F%2Fbackend-management.stylemap.svc.cluster.local%3A8081%2Factuator%2Fhealth%2Freadiness`.

## Результаты демонстрации на скринкасте

На скринкасте сначала показано штатное состояние системы. PostgreSQL availability находилась около `1.00` при SLO `0.99`; backend readiness по Blackbox Exporter также была успешной (`probe_success=1`) и соответствовала SLO `0.99`. Для публикаций были созданы свежие операции, после чего средняя длительность публикации находилась ниже порога `1 с`, а latency compliance была около `1.00` при SLO `0.95`. При выполнении всех трёх SLO итоговый demo SLA имел значение `1`.

Для управляемой деградации было показано исходное значение `STYLEMAP_DEMO_PUBLISH_DELAY_MS=0`, после чего ConfigMap изменён на `2500` мс и backend перезапущен:

```sh
kubectl patch configmap stylemap-config -n stylemap --type merge -p '{"data":{"STYLEMAP_DEMO_PUBLISH_DELAY_MS":"2500"}}'
kubectl rollout restart deployment/backend -n stylemap
kubectl rollout status deployment/backend -n stylemap
```

После готовности нового Pod были выполнены несколько публикаций. Искусственная задержка находится внутри измеряемого Timer, поэтому реальная метрика publication duration выросла: средняя длительность публикации стала выше порога `1 с` (примерно `2.5–3 с`). Интервалы со средней длительностью больше одной секунды стали плохими, latency compliance опустилась ниже SLO `0.95`, поэтому SLO публикации был нарушен.

При этом PostgreSQL продолжал оставаться доступным, а текущая Blackbox-проверка backend возвращала `probe_success=1`. Таким образом, демонстрация показывает деградацию именно производительности публикации, а не полный отказ приложения или базы данных.

После нарушения latency SLO итоговый demo SLA изменился с `1` на `0`, поскольку общий SLA определяется как логическое И трёх обязательных SLO. Это демонстрирует цепочку: изменение поведения приложения → рост измеряемой latency → ухудшение SLI → нарушение SLO → нарушение общего SLA.

После демонстрации значение задержки возвращено в `0`, а backend перезапущен:

```sh
kubectl patch configmap stylemap-config -n stylemap --type merge -p '{"data":{"STYLEMAP_DEMO_PUBLISH_DELAY_MS":"0"}}'
kubectl rollout restart deployment/backend -n stylemap
kubectl rollout status deployment/backend -n stylemap
```

Итоговое состояние для сдачи: искусственная задержка отключена, backend работает в штатном режиме. Отдельный отказ PostgreSQL и масштабирование backend до нуля на скринкасте не выполнялись: для демонстрации изменения SLI/SLO/SLA использована контролируемая деградация latency публикации, а работа prober показана через `probe_success` и readiness availability.
