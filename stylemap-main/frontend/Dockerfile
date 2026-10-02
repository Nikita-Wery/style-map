# ---------- 1. Сборка статики ----------
FROM node:22-alpine AS build

WORKDIR /app

# Сначала только манифесты — слой с зависимостями кешируется отдельно от исходников.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build


# ---------- 2. Раздача через nginx ----------
FROM nginx:1.27-alpine AS runtime

# Значения по умолчанию — топология docker-compose.
ENV STYLEMAP_BACKEND_HOST=backend:8080 \
    STYLEMAP_DNS_RESOLVER=127.0.0.11

# Конфиг попадает в /etc/nginx/templates и подставляется envsubst при старте,
# превращаясь в /etc/nginx/conf.d/default.conf (перезаписывая стандартный).
COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
