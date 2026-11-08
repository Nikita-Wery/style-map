# Скринкаст: запись готова, исходный план сохранён

Запись: [screencast/grafana.mp4](screencast/grafana.mp4). Размер 250150863 bytes; MP4 brand iso4, длительность 324.533 s (5:24.533). Video avc1/H.264, 1280×718, 30 fps, 9736 samples; audio track mp4a с ненулевыми samples. Контейнерные блоки и metadata проверены без изменения файла; полный playback/звук/содержание не декодировались из-за отсутствия видеодекодера. Эти данные не доказывают выполнение каждого пункта сценария ниже.

Перед отправкой открыть запись локальным плеером, проверить начало/середину/конец, звук и отсутствие секретов. MP4 игнорируется правилом `*.mp4` в Git: включить его в будущий архив явно. Повторная запись не требуется только из-за отсутствия ffprobe в среде проверки. Ниже — исходный план демонстрации, не фактические проверенные таймкоды.

1. 0:00–0:40: показать два dashboards в новой Grafana папке, существующий VictoriaMetrics datasource без credentials. Объяснить, что exporter/alerting/backend не менялись.
2. 0:40–1:40: показать Application Overview: health, RPS, status rates и 4xx/5xx. Запустить GET-трафик из README; ждать минимум два scrape-интервала. 401 — отказ anonymous API, не отказ сервера. 5xx не провоцировать.
3. 1:40–2:30: mean/p95/p99, через редактор показать `sum by (le)` и histogram_quantile. Объяснить units seconds, NaN/no data при отсутствии трафика, отсутствие безусловных zero fallbacks.
4. 2:30–3:20: heap/CPU/threads/GC и HikariCP. Связать utilization, pending saturation, timeout errors с USE; не называть system CPU достоверной node-метрикой.
5. 3:20–4:10: readiness availability за 30d, target 99%, coverage. Сослаться на прежний SLA. Назвать фактические значения на момент записи; прежние 98.9% availability / 1.57% coverage упоминать только как исторический контекст. Сказать, что короткая история не доказывает 30-дневное выполнение и это только readiness-компонент общего SLA.
6. 4:10–5:10: PostgreSQL 9628 adapted, фактическое значение pg_up (не обещать заранее 1), Database=stylemap, transactions/sessions/locks. Показать результат Query inspector; объяснить отсутствие Helm release label и исключённые legacy panels.
7. 5:10–5:40: показать JSON exports, отчёт и реальные screenshots. Не показывать пароли, kubeconfig, Secrets, session cookies или auth headers. Завершить остановкой записи, не остановкой кластера.

Если на записи данных нет — это не успех. Фактические screenshots и ссылки уже добавлены в отчёт; его SLA screenshot показывает 98.9% / 1.67%, а 1.57% относится к более раннему наблюдению. Общий SLA за 30 суток не подтверждён.
