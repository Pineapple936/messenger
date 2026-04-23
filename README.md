# Messenger

Backend мессенджера на микросервисной архитектуре.

Ключевые элементы:

- единая точка входа через `gateway-service`;
- JWT-аутентификация и валидация токена на gateway;
- асинхронные события через Kafka;
- модель `database-per-service` (у каждого сервиса своя база).

## Что реализовано

- Регистрация, логин, refresh/validate JWT.
- CRUD-операции по пользователям.
- Создание/получение/удаление чатов.
- Создание/чтение/пометка прочтения/удаление сообщений.
- Доставка событий в реальном времени через WebSocket в gateway.
- Событийная синхронизация между сервисами через Kafka.

## Архитектура сервисов

| Сервис                   |   Порт | Хранилище           | Назначение                                           |
| ------------------------ | -----: | ------------------- | ---------------------------------------------------- |
| `gateway-service`        | `8080` | нет                 | Маршрутизация, проверка токена, WebSocket-мост       |
| `authentication-service` | `8081` | PostgreSQL (`5433`) | Регистрация, логин, JWT, валидация токена            |
| `user-service`           | `8082` | PostgreSQL (`5432`) | Профиль пользователя                                 |
| `chat-service`           | `8083` | PostgreSQL (`5434`) | Чаты, участники, обновление `lastMessageAt`          |
| `message-service`        | `8084` | MongoDB (`27017`)   | Хранение сообщений, read-статусы, публикация событий |
| `common-libs`            |      - | -                   | Общие DTO, мапперы, константы                        |

## Как проходят запросы

1. Клиент отправляет запрос на `gateway-service`.
2. Для всех путей, кроме `/auth/**`, gateway вызывает `/auth/validate`.
3. Если токен валиден, gateway добавляет заголовок `X-User-Id` и проксирует запрос в целевой сервис.
4. Если токен невалиден, возвращается `401 Unauthorized`.

Упрощенная схема:

```text
Client -> Gateway -> Auth(validate JWT) -> Target service
                     \-> 401 при невалидном токене
```

## Основные сценарии

### Регистрация

1. `POST /auth/register` создает учетные данные в `authentication-service`.
2. `authentication-service` создает профиль в `user-service` (внутренний вызов).
3. В ответ возвращается пара токенов (`token`, `refreshToken`).

### Отправка сообщения

1. `POST /message` сохраняет сообщение в `message-service` (MongoDB).
2. `message-service` публикует событие в Kafka (`chat-messages`).
3. `chat-service` обновляет `lastMessageAt` у чата.
4. `gateway-service` читает событие и отправляет его онлайн-участникам чата по WebSocket.

### Прочтение сообщения

1. `PUT /message/read/{messageId}` или `PUT /message/read`.
2. `message-service` меняет статус `readStatus`.
3. Публикуется событие `message-read-event`.
4. `gateway-service` отправляет read-событие второму участнику чата.

### Удаление пользователя

1. `DELETE /user` удаляет профиль и учетные данные (через вызов в `auth-service`).
2. `user-service` публикует `user-delete`.
3. `chat-service` удаляет чаты пользователя и публикует `chat-delete`.
4. `message-service` удаляет сообщения удаленных чатов.

## API (через gateway)

### Auth (`/auth`)

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `GET /auth/validate`
- `DELETE /auth` (внутренний вызов, обычно вызывается из `user-service`)

### User (`/user`)

- `POST /user`
- `GET /user/{userId}`
- `GET /user/me`
- `GET /user/exists/{userId}`
- `POST /user/edit`
- `DELETE /user`

### Chat (`/chat`)

- `POST /chat`
- `GET /chat?limit={limit}&offset={offset}`
- `GET /chat/{chatId}/users/{userId}/exists`
- `GET /chat/{chatId}/users`
- `DELETE /chat/{chatId}`

### Message (`/message`)

- `POST /message`
- `GET /message/chat/{chatId}?limit={limit}&offset={offset}`
- `PUT /message/read/{messageId}`
- `PUT /message/read`
- `DELETE /message/{messageId}`

## Kafka топики и роли

| Топик                | Producer          | Consumer                          | Назначение                            |
| -------------------- | ----------------- | --------------------------------- | ------------------------------------- |
| `chat-messages`      | `message-service` | `chat-service`, `gateway-service` | Новые сообщения                       |
| `message-read-event` | `message-service` | `gateway-service`                 | События прочтения                     |
| `message-delete-event` | `message-service` | `gateway-service`               | События удаления сообщений            |
| `chat-delete`        | `chat-service`    | `message-service`                 | Каскадное удаление сообщений чата     |
| `user-delete`        | `user-service`    | `chat-service`                    | Каскадное удаление чатов пользователя |

## Конфигурация

Для локального запуска `.env` не требуется: в `docker-compose` уже заданы значения по умолчанию.

Ключевые переменные окружения:

- `KAFKA_BOOTSTRAP_SERVERS` (по умолчанию `localhost:9092`);
- `AUTH_SERVICE_URL`, `USER_SERVICE_URL`, `CHAT_SERVICE_URL`, `MESSAGE_SERVICE_URL`;
- `AUTH_SERVICE_URI`, `USER_SERVICE_URI`, `CHAT_SERVICE_URI`, `MESSAGE_SERVICE_URI` для gateway;
- для PostgreSQL: `DB_NAME`, `DB_USER`, `DB_PASSWORD`;
- для MongoDB: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_AUTH_SOURCE`, `DB_USERNAME`, `DB_PASSWORD`.

## Структура репозитория

```text
.
├── authentication-service
├── chat-service
├── common-libs
├── gateway-service
├── message-service
├── user-service
└── docker-compose.yaml   # Kafka broker
```

## Локальный запуск

Требования:

- Java 21+
- Maven
- Docker + Docker Compose

### 1) Поднять Kafka

Из корня репозитория:

```bash
docker compose up -d
```

### 2) Поднять базы данных

Из корня репозитория:

```bash
docker compose -f authentication-service/docker-compose.yml up -d
docker compose -f user-service/docker-compose.yml up -d
docker compose -f chat-service/docker-compose.yaml up -d
docker compose -f message-service/docker-compose.yaml up -d
```

### 3) Собрать `common-libs`

```bash
cd common-libs
mvn clean install -DskipTests
```

### 4) Запустить сервисы

Каждую команду запускать в отдельном терминале:

```bash
cd authentication-service && mvn spring-boot:run
cd user-service && mvn spring-boot:run
cd chat-service && mvn spring-boot:run
cd message-service && mvn spring-boot:run
cd gateway-service && mvn spring-boot:run
```

### 5) Проверить, что сервисы поднялись

- gateway: `http://localhost:8080`
- auth: `http://localhost:8081`
- user: `http://localhost:8082`
- chat: `http://localhost:8083`
- message: `http://localhost:8084`

## Итог

Сейчас это рабочий backend мессенджера на микросервисах с JWT, Kafka и WebSocket-доставкой.
Сервисы разделены по ответственности и по хранилищам (`database-per-service`), запуск локально описан выше.
