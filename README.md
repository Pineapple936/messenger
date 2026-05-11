# Messenger

Полноценный мессенджер: микросервисный Java-бэкенд + React-фронтенд.

> **Примечание:** фронтенд написан искусственным интеллектом.

## Что реализовано

- Регистрация, логин, refresh/validate JWT.
- CRUD-операции по пользователям, поиск по тегу, аватары.
- Приватные и групповые чаты с ролями (OWNER / ADMIN / MEMBER).
- Создание/чтение/редактирование/удаление сообщений с вложениями (фото).
- Цитирование сообщений (repliedMessageId).
- Реакции на сообщения (эмодзи).
- Доставка событий в реальном времени через WebSocket в gateway.
- Индикатор набора текста («печатает...»), присутствие (онлайн/оффлайн).
- Загрузка и отдача медиафайлов через Yandex Disk (media-service).
- Событийная синхронизация между сервисами через Kafka.
- Redis-кеш membership в `message-service` и rate limiter в `gateway-service`.

## Архитектура сервисов

| Сервис                   |   Порт | Хранилище                          | Назначение                                                 |
| ------------------------ | -----: | ---------------------------------- | ---------------------------------------------------------- |
| `gateway-service`        | `8080` | Redis (`6380`)                     | Маршрутизация, JWT-проверка, rate limiting, WebSocket-мост |
| `authentication-service` | `8081` | PostgreSQL (`5433`)                | Регистрация, логин, JWT, валидация токена                  |
| `user-service`           | `8082` | PostgreSQL (`5432`)                | Профиль пользователя, аватары, теги, поиск                 |
| `chat-service`           | `8083` | PostgreSQL (`5434`)                | Чаты, участники, роли, обновление `lastMessageAt`          |
| `message-service`        | `8084` | MongoDB (`27017`) + Redis (`6379`) | Сообщения, вложения, цитаты, статусы прочтения, Redis-кеш  |
| `reaction-service`       | `8085` | PostgreSQL (`5435`)                | Реакции на сообщения                                       |
| `media-service`          | `8086` | Yandex Disk                        | Загрузка и отдача медиафайлов                              |
| `common-libs`            |      — | —                                  | Общие DTO, маперы, константы                               |

## Как проходят запросы

```text
Client ──► Gateway ──► Auth (validate JWT) ──► Target service
                  └──► 401 при невалидном токене
```

1. Клиент отправляет запрос на `gateway-service` (`:8080`).
2. Для всех путей, кроме `/auth/**`, gateway вызывает `/auth/validate` и применяет rate limiting через Redis.
3. Если токен валиден, gateway добавляет заголовок `X-User-Id` и проксирует запрос в целевой сервис.

## Основные сценарии

### Регистрация

1. `POST /auth/register` создаёт учётные данные в `authentication-service`.
2. `authentication-service` создаёт профиль в `user-service` (внутренний вызов).
3. В ответ возвращается пара токенов (`token`, `refreshToken`).

### Отправка сообщения

1. `POST /message` сохраняет сообщение в `message-service` (MongoDB).
2. `message-service` проверяет membership через Redis-кеш и при необходимости через `chat-service`.
3. `message-service` публикует событие в Kafka топики `chat-messages` и `gateway-message-events`.
4. `chat-service` обновляет `lastMessageAt` у чата.
5. `gateway-service` читает событие из `gateway-message-events` и отправляет его онлайн-участникам по WebSocket.

### Реакция на сообщение

1. `POST /reaction/add` добавляет реакцию в `reaction-service` (PostgreSQL).
2. `reaction-service` публикует событие в `gateway-reaction-events`.
3. `gateway-service` доставляет событие участникам чата по WebSocket.

### Индикатор набора текста

1. Клиент отправляет по WebSocket сообщение `{"type":"typing","chatId":N}`.
2. `gateway-service` запрашивает участников чата у `chat-service` и рассылает typing-событие остальным онлайн-участникам.
3. На фронтенде typing отображается в заголовке активного чата и в строке предпросмотра в списке чатов.

### Загрузка медиафайлов

1. `POST /media/upload` (multipart) принимает файл в `media-service`.
2. `media-service` загружает файл на Yandex Disk и возвращает `{ url, fileName }`.
3. `GET /media/download/{fileName}` и `GET /media/proxy?publicUrl=...` — отдача файлов через gateway.

### Редактирование и удаление сообщения

1. `PUT /message/edit` меняет содержимое сообщения.
2. `DELETE /message/{messageId}` доступно только владельцу сообщения.
3. Изменения публикуются в `gateway-message-events` и доставляются участникам по WebSocket.

### Прочтение сообщения

1. `PUT /message/read/{messageId}` или `PUT /message/read` обновляет `readStatus`.
2. Gateway доставляет read-событие остальным участникам чата.

### Удаление пользователя

1. `DELETE /user` удаляет профиль и учётные данные (через вызов в `authentication-service`).
2. `user-service` публикует `user-delete`.
3. `chat-service` удаляет чаты пользователя и публикует `chat-delete`.
4. `message-service` и `reaction-service` удаляют данные удалённых чатов.

## API (через gateway на `:8080`)

### Auth (`/auth`)

| Метод    | Путь             | Описание                             |
| -------- | ---------------- | ------------------------------------ |
| `POST`   | `/auth/register` | Регистрация                          |
| `POST`   | `/auth/login`    | Логин, возвращает токены             |
| `POST`   | `/auth/refresh`  | Обновление токена                    |
| `GET`    | `/auth/validate` | Валидация токена (внутренний вызов)  |
| `DELETE` | `/auth`          | Удаление учётных данных (внутренний) |

### User (`/user`)

| Метод    | Путь                     | Описание                            |
| -------- | ------------------------ | ----------------------------------- |
| `POST`   | `/user`                  | Создание профиля (внутренний)       |
| `GET`    | `/user/me`               | Мой профиль                         |
| `GET`    | `/user/{userId}`         | Профиль по ID                       |
| `GET`    | `/user/search?tag={tag}` | Поиск по тегу (до 10 результатов)   |
| `GET`    | `/user/exists/{userId}`  | Проверка существования пользователя |
| `POST`   | `/user/edit`             | Редактирование профиля              |
| `DELETE` | `/user`                  | Удаление аккаунта                   |

### Chat (`/chat`)

| Метод    | Путь                                   | Описание                                     |
| -------- | -------------------------------------- | -------------------------------------------- |
| `POST`   | `/chat`                                | Создание чата                                |
| `POST`   | `/chat/users`                          | Добавить участника в групповой чат           |
| `GET`    | `/chat?limit=&offset=`                 | Список чатов с пагинацией                    |
| `PUT`    | `/chat/{chatId}/name/{newName}`        | Переименовать чат (только OWNER/ADMIN)       |
| `PUT`    | `/chat/{chatId}/my-name`               | Установить своё имя чата                     |
| `PUT`    | `/chat/{chatId}/avatar`                | Обновить аватар чата                         |
| `PUT`    | `/chat/role`                           | Изменить роль участника (OWNER/ADMIN/MEMBER) |
| `GET`    | `/chat/{chatId}/users`                 | Список участников                            |
| `GET`    | `/chat/{chatId}/users/{userId}/exists` | Проверить наличие пользователя в чате        |
| `DELETE` | `/chat/{chatId}`                       | Удалить чат                                  |
| `DELETE` | `/chat/leave/{chatId}`                 | Покинуть чат                                 |

### Message (`/message`)

| Метод    | Путь                                    | Описание                                    |
| -------- | --------------------------------------- | ------------------------------------------- |
| `POST`   | `/message`                              | Отправить сообщение (текст, фото, цитата)   |
| `GET`    | `/message/chat/{chatId}?limit=&offset=` | История сообщений с пагинацией              |
| `PUT`    | `/message/edit`                         | Редактировать сообщение                     |
| `PUT`    | `/message/read/{messageId}`             | Отметить сообщение как прочитанное          |
| `PUT`    | `/message/read`                         | Отметить все сообщения чата как прочитанные |
| `DELETE` | `/message/{messageId}`                  | Удалить сообщение                           |

### Reaction (`/reaction`)

| Метод    | Путь                                   | Описание                                          |
| -------- | -------------------------------------- | ------------------------------------------------- |
| `POST`   | `/reaction/add`                        | Добавить реакцию                                  |
| `GET`    | `/reaction/message/{messageId}`        | Реакции на сообщение                              |
| `POST`   | `/reaction/message/batchByUser`        | Реакции текущего пользователя на список сообщений |
| `DELETE` | `/reaction/message/{messageId}/{type}` | Удалить конкретную реакцию                        |
| `DELETE` | `/reaction/message/{messageId}`        | Удалить все реакции пользователя на сообщение     |

### Media (`/media`)

| Метод    | Путь                         | Описание                                                   |
| -------- | ---------------------------- | ---------------------------------------------------------- |
| `POST`   | `/media/upload`              | Загрузить файл (multipart), возвращает `{ url, fileName }` |
| `GET`    | `/media/download/{fileName}` | Скачать файл по имени                                      |
| `GET`    | `/media/proxy?publicUrl=`    | Проксировать публичный URL                                 |
| `GET`    | `/media/list`                | Список файлов                                              |
| `DELETE` | `/media/{fileName}`          | Удалить файл                                               |
| `DELETE` | `/media`                     | Удалить список файлов (batch)                              |

## WebSocket

Подключение: `ws://localhost:8080/ws` с заголовком `Authorization: Bearer <token>`.

| Направление     | Тип события          | Описание                                |
| --------------- | -------------------- | --------------------------------------- |
| Клиент → Сервер | `presence_subscribe` | Подписаться на присутствие пользователя |
| Клиент → Сервер | `typing`             | Уведомление о наборе текста в чате      |
| Сервер → Клиент | `MESSAGE_CREATED`    | Новое сообщение                         |
| Сервер → Клиент | `MESSAGE_EDITED`     | Сообщение отредактировано               |
| Сервер → Клиент | `MESSAGE_DELETED`    | Сообщение удалено                       |
| Сервер → Клиент | `MESSAGE_READ`       | Сообщение прочитано                     |
| Сервер → Клиент | `REACTION_UPDATED`   | Реакция добавлена/удалена               |
| Сервер → Клиент | `typing`             | Пользователь набирает текст             |
| Сервер → Клиент | `presence`           | Обновление статуса присутствия          |

## Kafka топики

| Топик                     | Producer           | Consumer(s)                           | Назначение                                       |
| ------------------------- | ------------------ | ------------------------------------- | ------------------------------------------------ |
| `chat-messages`           | `message-service`  | `chat-service`                        | Обновление `lastMessageAt`                       |
| `gateway-message-events`  | `message-service`  | `gateway-service`                     | Доставка событий сообщений по WebSocket          |
| `gateway-reaction-events` | `reaction-service` | `gateway-service`                     | Доставка событий реакций по WebSocket            |
| `message-delete`          | `message-service`  | `reaction-service`                    | Каскадное удаление реакций удалённых сообщений   |
| `chat-delete`             | `chat-service`     | `message-service`, `reaction-service` | Каскадное удаление сообщений и реакций чата      |
| `user-delete`             | `user-service`     | `chat-service`                        | Каскадное удаление чатов удалённого пользователя |

## Конфигурация

Для локального запуска `.env` не требуется — в `docker-compose` заданы значения по умолчанию.

| Переменная                | Сервис(ы)                        | Значение по умолчанию   | Описание                |
| ------------------------- | -------------------------------- | ----------------------- | ----------------------- |
| `KAFKA_BOOTSTRAP_SERVERS` | все сервисы                      | `localhost:9092`        | Адрес Kafka брокера     |
| `DB_NAME`                 | auth, user, chat, reaction       | `auth` / `user` / ...   | Имя базы данных         |
| `DB_USER`                 | auth, user, chat, reaction       | `postgres`              | Пользователь PostgreSQL |
| `DB_PASSWORD`             | auth, user, chat, reaction       | `postgres`              | Пароль PostgreSQL       |
| `DB_HOST`                 | message-service                  | `localhost`             | Хост MongoDB            |
| `DB_USERNAME`             | message-service                  | `admin`                 | Пользователь MongoDB    |
| `REDIS_PASSWORD`          | gateway-service, message-service | `password`              | Пароль Redis            |
| `DISK_TOKEN`              | media-service                    | —                       | OAuth-токен Yandex Disk |
| `AUTH_SERVICE_URI`        | gateway-service                  | `http://localhost:8081` | URL auth-service        |
| `USER_SERVICE_URI`        | gateway-service                  | `http://localhost:8082` | URL user-service        |
| `CHAT_SERVICE_URI`        | gateway-service                  | `http://localhost:8083` | URL chat-service        |
| `MESSAGE_SERVICE_URI`     | gateway-service                  | `http://localhost:8084` | URL message-service     |
| `REACTION_SERVICE_URI`    | gateway-service                  | `http://localhost:8085` | URL reaction-service    |
| `MEDIA_SERVICE_URI`       | gateway-service                  | `http://localhost:8086` | URL media-service       |

## Структура репозитория

```text
.
├── authentication-service/   # JWT, регистрация, логин
├── chat-service/             # Чаты, участники, роли
├── common-libs/              # Общие DTO, маперы, константы
├── frontend/                 # React 18 + TypeScript + Vite (SPA)
├── gateway-service/          # Маршрутизация, WebSocket, rate limiting
├── media-service/            # Загрузка/отдача медиа через Yandex Disk
├── message-service/          # Сообщения (MongoDB), цитаты, вложения
├── reaction-service/         # Реакции на сообщения
├── user-service/             # Профили пользователей, аватары, поиск
└── docker-compose.yaml       # Kafka broker
```

## Локальный запуск

**Требования:** Java 21+, Maven, Docker + Docker Compose, Node.js 18+

### 1. Запустить Kafka

```bash
docker compose up -d
```

### 2. Запустить базы данных

```bash
docker compose -f authentication-service/docker-compose.yml up -d
docker compose -f user-service/docker-compose.yml up -d
docker compose -f chat-service/docker-compose.yaml up -d
docker compose -f gateway-service/docker-compose.yaml up -d
docker compose -f message-service/docker-compose.yaml up -d
docker compose -f reaction-service/docker-compose.yaml up -d
```

### 3. Собрать common-libs

```bash
cd common-libs && mvn clean install -DskipTests
```

### 4. Запустить бэкенд-сервисы

Каждый сервис — в отдельном терминале:

```bash
cd authentication-service && mvn spring-boot:run
cd user-service           && mvn spring-boot:run
cd chat-service           && mvn spring-boot:run
cd message-service        && mvn spring-boot:run
cd reaction-service       && mvn spring-boot:run
cd media-service          && mvn spring-boot:run   # нужен DISK_TOKEN
cd gateway-service        && mvn spring-boot:run
```

### Адреса сервисов

| Сервис             | Адрес                   |
| ------------------ | ----------------------- |
| Frontend           | `http://localhost:5173` |
| Gateway (API + WS) | `http://localhost:8080` |
| Auth               | `http://localhost:8081` |
| User               | `http://localhost:8082` |
| Chat               | `http://localhost:8083` |
| Message            | `http://localhost:8084` |
| Reaction           | `http://localhost:8085` |
| Media              | `http://localhost:8086` |
| Redis (gateway)    | `localhost:6380`        |
| Redis (message)    | `localhost:6379`        |
| MongoDB            | `localhost:27017`       |
