# StyleMap

Социальная платформа для публикации аутфитов: посты с фото/видео, вещи и ссылки
«где купить», хештеги, оценки, подписки и конвертация валют.

## Запуск для проверки задания

Основное окружение — **Docker Compose**. Нужны Docker с Compose v2 и доступ
к интернету для загрузки образов и зависимостей. Java, Maven и Node.js на хосте
не нужны: Docker собирает backend из исходников и frontend из npm lockfile.
Все команды выполняются из каталога с `docker-compose.yml`.

```bash
cp .env.example .env
# При необходимости отредактируйте .env (см. ниже).
docker compose up --build
```

Открыть **http://localhost:3000**. Backend доступен напрямую на
**http://localhost:8080**, PostgreSQL — на **localhost:5434**.
Порты 3000, 8080 и 5434 должны быть свободны.

`.env.example` содержит только вымышленные значения. Укажите собственный
`DB_PASSWORD`. Google OAuth параметры в шаблоне фиктивны; вход через Google требует
настоящих `GOOGLE_CLIENT_ID` и `GOOGLE_CLIENT_SECRET`. В консоли Google зарегистрируйте redirect URI:
`http://localhost:3000/login/oauth2/code/google`.

Регистрация по паролю отправляет код подтверждения через SMTP. Для рабочего
сценария задайте `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` и
`MAIL_SENDER_EMAIL`; для Gmail нужен пароль приложения. Фиктивные SMTP данные
не обеспечивают отправку писем. Почтовая конфигурация использует авторизацию
и STARTTLS. Реальные секреты храните только в локальном `.env`.

### Порядок старта и диагностика

Compose ждёт готовности PostgreSQL, запускает одноразовый сервис
`liquibase-migrations`, затем backend и после его readiness-проверки frontend.
Статус завершившегося сервиса миграций `Exited (0)` нормален.

```bash
docker compose ps -a
docker compose logs backend liquibase-migrations
docker compose down
```

Не публикуйте диагностические выводы без проверки: конфигурация и сообщения
ошибок могут содержать чувствительные данные. Не используйте реальные секреты
при отправке исходников преподавателю.

PostgreSQL хранится в `my_db_files/`, медиа — в `uploads/`. Эти каталоги остаются
после `docker compose down` и пересоздания контейнеров. Изменение DB_USER,
DB_PASSWORD или DB_NAME в `.env` не изменяет учётные данные уже созданной БД.
Не удаляйте каталоги, если нужны сохранённые данные.

Логи приложения выводятся в stdout, без файловых appender-ов.
`SHOW_SQL=false` по умолчанию; для локальной диагностики можно поставить `true`.

## Архитектура и стек

- Backend: Java 25, Spring Boot 4.0.3, Spring Security, JPA/Hibernate, Liquibase.
- База: PostgreSQL 17 с `ltree` и JSONB.
- Frontend: React 19, TypeScript, Vite, Tailwind CSS 4, React Router.
- nginx отдаёт SPA и проксирует `/api/`, `/auth/`, `/uploads/`, `/oauth2/`
  и `/login/oauth2/` на backend.

Frontend обращается к backend по HTTP через `fetch`. Используются сессионная
cookie и CSRF заголовок; nginx сохраняет единый origin. Старые Thymeleaf страницы
остаются на backend: `/auth/login`, `/showroom/home`, `/admin`.

Сборка backend многостадийная: JDK и Maven wrapper работают только в build-слое,
финальный образ содержит JRE и JAR. Frontend собирается через `npm ci`, затем
готовая статика переносится в nginx. Миграции лежат в исходниках и выполняются
отдельным процессом; в Compose миграции внутри backend отключены.

## API и CRUD

Все `/api/` маршруты, кроме аутентификации, требуют входа; `/api/admin/` требует
роль ADMIN. Создание поста принимает multipart `file` и JSON-строку `postData`.

| Область | Маршруты |
|---|---|
| Аутентификация | `/api/auth/me`, `/csrf`, `/login`, `/register`, `/confirm`, `/logout` |
| Посты | POST `/api/posts`, GET `/api/posts/feed`, GET `/api/posts/{id}`, GET `/api/posts/user/{userId}` |
| Изменение поста | PUT и DELETE `/api/actions/posts/{id}` |
| Оценки | POST `/api/actions/posts/{id}/rate?score=…` |
| Подписки | POST `/api/actions/users/{id}/toggle-follow` |
| Валюты | GET `/api/actions/convert-currency` |
| Профили | GET `/api/users/{username}` |
| Хештеги | GET `/api/hashtags/search?q=…` |
| Администрирование | `/api/admin/users`, `/api/admin/posts`, `/api/admin/hashtags` |
| Готовность backend | GET `/actuator/health/readiness` (включает БД) |

Администратор не создаётся автоматически. Процедура первоначального назначения
роли должна быть проверена и задокументирована на этапе runtime-проверки.

## Локальная разработка

Нужны Java 25 и Node.js 22; Maven загружается wrapper-ом. Поднимите PostgreSQL:

```bash
docker compose up -d db
```

Перед запуском backend экспортируйте переменные из `.env` в окружение процесса
(сам Spring Boot файл `.env` не читает). Для произвольного имени БД задайте
`SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/<DB_NAME>`.
`APP_STORAGE_UPLOAD_DIR` по умолчанию равен `./uploads`.
Локально Liquibase включён и выполняется при старте приложения.

```bash
./mvnw spring-boot:run
```

В другом терминале:

```bash
cd frontend
npm ci
npm run dev
```

Открыть **http://localhost:5173**. Vite проксирует запросы на localhost:8080.
При другом адресе задайте `VITE_PROXY_TARGET` в окружении команды Vite.

## Ограничения и статус проверки

Backend рассчитан на один экземпляр. Сессии находятся в памяти и теряются при
перезапуске. Медиа сохраняется в локальном каталоге; в PostgreSQL хранятся записи
и пути, но не байты файлов. Полная stateless-модель и распределённое хранение
в рамки задания не добавляются. Конвертация валют зависит от внешнего HTTP API,
регистрация — от SMTP, Google OAuth — от внешнего провайдера.

Статические проверки и результаты сборок фиксируются отдельно. Успешную работу
всех маршрутов, миграций и сценариев нельзя считать подтверждённой до проверки
запущенного приложения. Kubernetes для сдачи не используется.

## Состав ZIP

В архив включаются `src/`, `frontend/src/`, frontend конфигурация и lockfile,
`pom.xml`, `.mvn/`, оба wrapper-скрипта, Dockerfiles, Compose, README и безопасный
`.env.example`. После runtime-проверки будет добавлен обязательный `Отчёт.md`.

Не включайте `.git/`, `.idea/`, `target/`, `node_modules/`, `frontend/dist/`,
реальный `.env` и любые секреты, `my_db_files/`, `uploads/`, `logs/` и логи.
Правила `.gitignore` не фильтруют ZIP автоматически: состав архива нужно проверять.
