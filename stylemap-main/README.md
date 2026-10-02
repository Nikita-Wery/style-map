# StyleMap

Социальная платформа для аутфитов: лента постов, хештеги, рейтинги, подписки и список
вещей «где купить» с конвертацией валют.

Три сервиса: **frontend** (nginx + React SPA), **backend** (Spring Boot 4 / Java 25),
**db** (PostgreSQL 17).

```
Браузер ──▶ nginx ──┬── /                             → статика React (index.html + /assets)
                    ├── /api/**                       → backend :8080
                    ├── /auth/**                      → backend :8080
                    ├── /uploads/**                   → backend :8080
                    └── /oauth2/**, /login/oauth2/**  → backend :8080
```

nginx проксирует API на бэкенд, поэтому для браузера всё живёт на одном origin.
Благодаря этому сессионная cookie `JSESSIONID` и CSRF-токен работают без CORS.

---

## Google OAuth2: почему нужно регистрировать redirect URI

**Короткий ответ:** это требование Google, а не архитектуры проекта. И `localhost:3000`
— не «обязательное» значение, а просто тот адрес, по которому браузер открывал
приложение в docker-compose. В Kubernetes адрес другой — значит, регистрировать надо
другой.

### Механика

1. Пользователь нажимает «Войти через Google» → браузер идёт на
   `/oauth2/authorization/google` **по тому адресу, который открыт в строке браузера**.
2. Spring Security формирует `redirect_uri` из этого запроса: схема + хост + порт
   (через заголовки `X-Forwarded-Proto` / `X-Forwarded-Host`, которые проставляет nginx).
3. Браузер уходит на Google с этим `redirect_uri` в параметрах.
4. Google сверяет его со списком *Authorized redirect URIs* в вашем OAuth-клиенте.
   **Совпадение должно быть точным, посимвольным.** Если его нет — Google отвечает
   ошибкой `redirect_uri_mismatch` и вход не выполняется.

Так сделано для безопасности: без белого списка злоумышленник мог бы подставить свой
`redirect_uri` и перехватить код авторизации.

### Ограничения Google

- **HTTPS обязателен** для всех адресов, кроме `localhost` и `127.0.0.1`.
- **IP-адрес в `redirect_uri` не принимается.**

Поэтому `http://192.168.49.2:30080/login/oauth2/code/google` (NodePort minikube)
**не заработает, даже если вписать его в консоль**. Это ключевой момент для Kubernetes.

### Как получить рабочий адрес в Kubernetes

**Вариант A — быстро, для локального кластера (рекомендуется для minikube)**

Пробрасываем сервис на localhost — тогда Google разрешает обычный HTTP:

```bash
kubectl -n stylemap port-forward svc/front-service 8080:80
```

Открывать `http://localhost:8080`, зарегистрировать в Google Console:

```
http://localhost:8080/login/oauth2/code/google
```

**Вариант B — по-настоящему (Ingress + домен + TLS)**

```bash
minikube addons enable ingress
```

Добавляете Ingress с реальным доменом и сертификатом (например, cert-manager и
Let's Encrypt), открываете `https://stylemap.example.com`, регистрируете:

```
https://stylemap.example.com/login/oauth2/code/google
```

### Если нужен жёстко зафиксированный адрес

По умолчанию Spring вычисляет `redirect_uri` из запроса — это правильно для любого
окружения. Если хочется зафиксировать его явно (например, чтобы не зависеть от того,
какие заголовки присылает Ingress), задайте переменную окружения:

```
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://stylemap.example.com/login/oauth2/code/google
```

При этом адрес всё равно должен быть в белом списке Google.

> Вход по логину и паролю (`/api/auth/login`) и регистрация от OAuth никак не зависят
> и работают без всякой настройки Google.

---

## Быстрый старт: Docker Compose

### 1. Соберите jar бэкенда

`Dockerfile` бэкенда копирует уже собранный `target/*.jar`, поэтому сначала:

```bash
./mvnw clean package -DskipTests
```

### 2. Проверьте `.env`

```dotenv
DB_NAME=postgres
DB_USER=postgres
DB_PASSWORD=postgres
CLIENT_ID=<google-oauth-client-id>
CLIENT_SECRET=<google-oauth-client-secret>
SENDER_EMAIL=<gmail-адрес для писем подтверждения>
GOOGLE_APP=<app password от Gmail>
```

### 3. Запустите

```bash
docker compose up -d --build
```

Приложение: **http://localhost:3000**

Liquibase в compose вынесен в отдельный сервис `liquibase-migrations`, который
отрабатывает до старта бэкенда (у приложения `SPRING_LIQUIBASE_ENABLED=FALSE`).

> Прежние Thymeleaf-страницы по-прежнему доступны напрямую на **http://localhost:8080**
> (`/auth/login`, `/showroom/home`, `/admin`) — оставлены как рабочий фолбэк.

---

## Kubernetes

Манифесты лежат в `k8s/` и собираются через Kustomize:

```bash
kubectl apply -k k8s/
```

### Переменные окружения фронтенда (важно!)

Образ nginx универсальный: конфиг `frontend/default.conf.template` подставляется через
`envsubst` при старте контейнера. Значения по умолчанию (в `Dockerfile`) — топология
docker-compose, в Kubernetes они переопределяются в
`k8s/frontend/frontend-deployment.yaml`:

| Переменная | docker-compose | Kubernetes |
|---|---|---|
| `STYLEMAP_BACKEND_HOST` | `backend:8080` | `back-service.stylemap.svc.cluster.local:8080` |
| `STYLEMAP_DNS_RESOLVER` | `127.0.0.11` (DNS Docker) | `kube-dns.kube-system.svc.cluster.local` (CoreDNS) |

Два подводных камня, из-за которых всё API отдаёт **502 Bad Gateway**:

1. `127.0.0.11` — это встроенный DNS Docker, в Kubernetes его нет. В логе nginx будет
   `recv() failed (111: Connection refused) while resolving, resolver: 127.0.0.11:53`.
2. **nginx не применяет search-домены из `/etc/resolv.conf`.** Короткое имя
   `back-service` не резолвится, нужно полное:
   `back-service.<namespace>.svc.cluster.local`.

### Пробы

Бэкенд отдаёт `/actuator/health/liveness` и `/actuator/health/readiness`
(`management.endpoint.health.probes.enabled: true`). Пути `/actuator/health/**`
разрешены без аутентификации в `SecurityConfig`.

readiness проверяет и БД — пока база недоступна, трафик на под не идёт.
liveness от БД намеренно не зависит, иначе её падение вызывало бы перезапуск пода.

### Обновление образов

В манифестах стоит `imagePullPolicy: IfNotPresent`. Если пересобрать образ
**с тем же тегом**, Kubernetes не станет его скачивать. Поэтому либо поднимайте тег
(`:v2` → `:v3`), либо после сборки делайте:

```bash
kubectl -n stylemap rollout restart deployment/backend-deployment deployment/frontend-deployment
```

### Доступ к приложению

Сервисы опубликованы как NodePort:

| Сервис | NodePort | Назначение |
|---|---|---|
| `front-service` | `30080` | SPA + прокси на API |
| `back-service` | `30030` | бэкенд напрямую (Thymeleaf-страницы) |

---

## Локальная разработка (без Docker)

Нужны PostgreSQL и Java 25. Поднимите только базу:

```bash
docker compose up -d db
```

**Бэкенд** (порт 8080):

```bash
./mvnw spring-boot:run
```

**Фронтенд** (порт 5173, горячая перезагрузка):

```bash
cd frontend
npm install
npm run dev
```

Vite проксирует `/api`, `/auth`, `/uploads` и `/oauth2` на `http://localhost:8080`
(переопределяется через `VITE_PROXY_TARGET`), поэтому cookie и CSRF работают так же,
как в production. Открывайте **http://localhost:5173**.

### Полезные команды фронтенда

```bash
npm run build       # проверка типов (tsc --noEmit) + production-сборка в dist/
npm run typecheck   # только проверка типов
npm run preview     # локальный просмотр собранной статики
```

---

## Аутентификация

Используется **сессионная** аутентификация Spring Security — та же, что и для
Thymeleaf-страниц. Пользователь, вошедший через SPA, виден и на серверных страницах,
и наоборот.

Схема с CSRF:

1. При первом запросе Spring кладёт токен в cookie `XSRF-TOKEN` (доступна из JavaScript).
2. React читает её и отправляет значение в заголовке `X-XSRF-TOKEN` при каждом
   небезопасном запросе (`POST`/`PUT`/`PATCH`/`DELETE`).
3. Thymeleaf-формы продолжают работать через скрытое поле `_csrf`.

За это отвечают `SpaCsrfTokenRequestHandler` и `CsrfCookieFilter` в пакете `config`.

Для запросов к `/api/**` неавторизованный доступ возвращает **401 в формате JSON**
(а не редирект на HTML-страницу логина), чтобы SPA мог сам обработать ситуацию.

---

## REST API

### Аутентификация — `/api/auth`

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/api/auth/me` | Текущий пользователь или `401` |
| `GET` | `/api/auth/csrf` | Актуальный CSRF-токен |
| `POST` | `/api/auth/login` | Вход по `{username, password}` |
| `POST` | `/api/auth/register` | Регистрация, отправляет код на почту |
| `POST` | `/api/auth/confirm` | Подтверждение по `{email, code}` |
| `POST` | `/api/auth/logout` | Выход, инвалидация сессии |

### Посты — `/api/posts`, `/api/actions`

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/api/posts/feed` | Лента (`hashtag`, `page`, `size`, `sortBy`, `followingOnly`) |
| `GET` | `/api/posts/{id}` | Детали поста |
| `GET` | `/api/posts/user/{userId}` | Посты пользователя |
| `POST` | `/api/posts` | Создание поста (`multipart/form-data`) |
| `PUT` | `/api/actions/posts/{id}` | Редактирование (только автор) |
| `DELETE` | `/api/actions/posts/{id}` | Удаление (автор или админ) |
| `POST` | `/api/actions/posts/{id}/rate?score=` | Оценка 1–10 |
| `POST` | `/api/actions/users/{id}/toggle-follow` | Подписаться / отписаться |
| `GET` | `/api/actions/convert-currency` | Конвертация валюты |

### Прочее

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/api/users/{username}` | Профиль пользователя |
| `GET` | `/api/hashtags/search?q=` | Автокомплит хештегов |
| `GET/POST/PATCH/DELETE` | `/api/admin/users` | Управление пользователями (роль `ADMIN`) |
| `GET/POST/PUT/DELETE` | `/api/admin/posts` | Управление постами (роль `ADMIN`) |
| `GET/POST/PUT/DELETE` | `/api/admin/hashtags` | Управление хештегами (роль `ADMIN`) |
| `GET` | `/actuator/health/{liveness,readiness}` | Пробы Kubernetes |

---

## Структура проекта

```
.
├── docker-compose.yml              # db + liquibase-migrations + backend + frontend
├── Dockerfile                      # образ бэкенда (Spring Boot jar)
├── Dockerfile.liquibase            # образ с ченджлогами для сервиса миграций
├── pom.xml
├── k8s/                            # манифесты Kubernetes (Kustomize)
│   ├── kustomization.yaml
│   ├── namespace.yaml
│   ├── configMap/                  # backend-config
│   ├── data/                       # секреты и PVC
│   ├── db/                         # postgres StatefulSet + headless Service
│   ├── backend/                    # Deployment + Service + Job миграций
│   └── frontend/                   # Deployment + Service
├── src/main/java/ru/itis/stylemap/
│   ├── config/                     # SecurityConfig, CORS, CSRF для SPA
│   ├── controller/
│   │   ├── api/                    # REST-контроллеры для SPA
│   │   └── ...                     # Thymeleaf-контроллеры (фолбэк)
│   ├── dto/                        # DTO, зеркалятся в frontend/src/api/types.ts
│   └── service/
├── src/main/resources/
│   ├── application.yaml
│   ├── templates/                  # Thymeleaf-страницы (по-прежнему работают)
│   └── static/                     # css/js для Thymeleaf-страниц
└── frontend/                       # React SPA
    ├── Dockerfile                  # node build → nginx
    ├── default.conf.template       # SPA-fallback + прокси (envsubst при старте)
    ├── vite.config.ts              # dev-прокси на localhost:8080
    └── src/
        ├── api/                    # client.ts (fetch + CSRF), types.ts
        ├── auth/                   # AuthContext
        ├── components/             # TopNav, PostGrid, HashtagInput, ...
        └── pages/                  # Login, Feed, CreatePost, PostDetails, Profile, Admin
```

---

## Стек фронтенда

- **React 19** + **TypeScript**
- **Vite** — сборка и dev-сервер
- **Tailwind CSS 4** — утилиты + дизайн-токены в `src/index.css`
- **React Router 7** — клиентская маршрутизация

Маршруты SPA: `/login`, `/register`, `/confirm`, `/` (лента), `/create`,
`/post/:id`, `/user/:username`, `/profile`, `/admin`.
