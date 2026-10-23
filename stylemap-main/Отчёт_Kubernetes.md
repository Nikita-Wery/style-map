# Отчёт по развёртыванию StyleMap в Kubernetes

## 1. Цель работы

Цель работы --- развернуть приложение StyleMap и его зависимости в
одноузловом кластере Kubernetes на базе Minikube, описать инфраструктуру
Kubernetes-манифестами и проверить запуск, доступ к приложению и
горизонтальное масштабирование его сервисов.

StyleMap состоит из frontend на React/nginx, backend на Spring Boot,
PostgreSQL и отдельного процесса миграций Liquibase.

## 2. Использованные Kubernetes-абстракции

Для развёртывания используются следующие ресурсы Kubernetes:

  -----------------------------------------------------------------------
  Абстракция                          Назначение
  ----------------------------------- -----------------------------------
  `Namespace`                         `stylemap` --- изоляция ресурсов
                                      приложения

  `ConfigMap`                         `stylemap-config` --- несекретная
                                      конфигурация приложения

  `Secret`                            `stylemap-secret` ---
                                      чувствительные параметры окружения

  `PersistentVolumeClaim`             `postgres-data` для PostgreSQL и
                                      `backend-uploads` для загруженных
                                      файлов

  `Service`                           Внутренний доступ к PostgreSQL и
                                      backend, а также внешний доступ к
                                      frontend

  `StatefulSet`                       Запуск PostgreSQL с постоянным
                                      хранилищем

  `Job`                               Одноразовый запуск Liquibase для
                                      применения миграций

  `Deployment`                        Запуск и масштабирование backend и
                                      frontend
  -----------------------------------------------------------------------

Используются следующие манифесты:

``` text
k8s/
├── 00-namespace.yaml
├── 01-configmap.yaml
├── 02-secret.example.yaml
├── 03-postgres.yaml
├── 04-liquibase-job.yaml
├── 05-backend.yaml
└── 06-frontend.yaml
```

Файл `02-secret.example.yaml` является только шаблоном. Настоящие
значения Secret в архив не включаются.

## 3. Взаимодействие компонентов

Схема взаимодействия имеет следующий вид:

``` text
Браузер
   |
   v
frontend Service (NodePort :30080)
   |
   v
frontend Deployment (React + nginx)
   |
   | HTTP
   v
backend Service (ClusterIP)
   |
   v
backend Deployment
   |             \
   | JDBC         \ /app/uploads
   v               v
postgres Service   PVC backend-uploads
   |
   v
PostgreSQL StatefulSet
   |
   v
PVC postgres-data

Liquibase Job ---> postgres Service ---> PostgreSQL
```

Frontend доступен с компьютера пользователя через `NodePort` на порту
`30080`. nginx внутри frontend проксирует API-запросы на:

``` text
backend.stylemap.svc.cluster.local:8080
```

Backend обращается к PostgreSQL через Kubernetes Service `postgres`.
Благодаря Kubernetes DNS приложение не зависит от IP-адресов отдельных
Pod.

PostgreSQL запускается как `StatefulSet` с одной репликой. Данные БД
сохраняются в PVC `postgres-data`.

Миграции БД выполняются отдельным `Job` Liquibase. После успешного
применения миграций Job завершается со статусом `Completed`.

Backend запускается как `Deployment`. Загруженные пользователями файлы
сохраняются в PVC `backend-uploads`, примонтированном в `/app/uploads`.

## 4. Docker-образы

Для компонентов используются следующие образы:

  Компонент    Образ
  ------------ -----------------------------
  Backend      `stylemap-backend:k8s-v1`
  Frontend     `stylemap-frontend:latest`
  Liquibase    `stylemap-liquibase:latest`
  PostgreSQL   `postgres:17`

Backend, frontend и Liquibase собираются из Dockerfile проекта.
Локальные пользовательские образы доступны внутри Minikube и
используются с `imagePullPolicy: Never`.

Для работы с Docker-окружением Minikube может использоваться:

``` bash
eval $(minikube docker-env)
```

После сборки локальный образ также можно явно загрузить в Minikube:

``` bash
minikube image load <image>
```

В выполненном развёртывании образы приложения были собраны и доступны
локально в Minikube, поэтому внешний registry не требуется.

## 5. Развёртывание приложения

Манифесты применяются последовательно:

``` bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-configmap.yaml

# stylemap-secret создаётся отдельно из приватных значений

kubectl apply -f k8s/03-postgres.yaml
kubectl rollout status statefulset/postgres -n stylemap --timeout=180s

kubectl apply -f k8s/04-liquibase-job.yaml
kubectl wait --for=condition=complete job/liquibase -n stylemap --timeout=180s

kubectl apply -f k8s/05-backend.yaml
kubectl rollout status deployment/backend -n stylemap --timeout=180s

kubectl apply -f k8s/06-frontend.yaml
kubectl rollout status deployment/frontend -n stylemap --timeout=180s
```

Файл `k8s/02-secret.example.yaml` не применяется поверх рабочего Secret,
поскольку он содержит только пример структуры конфигурации.

Состояние ресурсов проверяется командами:

``` bash
kubectl get pods -n stylemap
kubectl get services -n stylemap
kubectl get pvc -n stylemap
```

В результате PostgreSQL, backend и frontend находятся в состоянии
`Running`, а Liquibase Job после выполнения --- в состоянии `Completed`.

## 6. Доступ к приложению

Frontend опубликован через Service типа `NodePort`:

``` text
30080
```

В Minikube адрес приложения можно получить командой:

``` bash
minikube service frontend -n stylemap --url
```

В ходе проверки frontend был доступен из браузера, nginx успешно
проксировал запросы на backend через Kubernetes DNS, а backend успешно
работал с PostgreSQL.

Для локальной демонстрации доставка электронной почты отключена.
Локальный администратор создаётся миграцией Liquibase, что позволяет
проверить авторизованную часть интерфейса без настройки SMTP.

## 7. Масштабирование

Изначально манифесты backend и frontend задают по одной реплике.

Для проверки горизонтального масштабирования количество реплик было
увеличено до двух:

``` bash
kubectl scale deployment backend --replicas=2 -n stylemap
kubectl scale deployment frontend --replicas=2 -n stylemap
```

Состояние проверялось командами:

``` bash
kubectl rollout status deployment/backend -n stylemap --timeout=180s
kubectl rollout status deployment/frontend -n stylemap --timeout=180s
kubectl get deployments,pods -n stylemap
kubectl get endpointslices -n stylemap -l kubernetes.io/service-name=backend
```

Оба Deployment достигли состояния `2/2 Ready`. В кластере одновременно
работали две реплики frontend и две реплики backend.

Service `backend` обнаружил обе backend-реплики и мог направлять запросы
к ним. После масштабирования дополнительно была проверена работа
авторизованного интерфейса: переходы между страницами и перезагрузка UI
продолжали работать.

Проверка проводилась в одноузловом Minikube и не является проверкой
production- или multi-node-масштабирования.

## 8. Постоянное хранение

Для хранения состояния используются два PVC:

-   `postgres-data` --- данные PostgreSQL;
-   `backend-uploads` --- загруженные пользователями медиафайлы.

Пересоздание Pod или Deployment не требует удаления этих PVC, поэтому
данные хранятся отдельно от жизненного цикла Pod.

Оба PVC используют режим `ReadWriteOnce`. Две backend-реплики успешно
работали с `backend-uploads` в одноузловом Minikube. Это не подтверждает
возможность аналогичного использования тома между несколькими
Kubernetes-узлами.

## 9. Результат

В ходе работы:

-   приложение и его зависимости развёрнуты в одноузловом Minikube;
-   подготовлены Kubernetes-манифесты для всех необходимых компонентов;
-   PostgreSQL запущен через `StatefulSet` с постоянным хранилищем;
-   миграции выполняются отдельным `Job` Liquibase;
-   backend и frontend запущены через `Deployment`;
-   внутреннее взаимодействие реализовано через `ClusterIP Service` и
    Kubernetes DNS;
-   frontend доступен с хоста через `NodePort`;
-   backend и frontend успешно масштабированы с одной до двух реплик;
-   после масштабирования приложение сохранило работоспособность;
-   конфигурация разделена между `ConfigMap` и `Secret`;
-   реальные секретные значения не включаются в архив задания.
