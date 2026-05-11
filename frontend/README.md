# Frontend (React + Vite)

UI client for the messenger microservices backend.

## Run

```bash
cd frontend
npm install
# optional: cp .env.example .env.local
npm run dev
```

Dev server starts on `http://localhost:5173`.

## Backend wiring

- REST calls use gateway routes through Vite proxy:
  - `/auth/**`
  - `/user/**`
  - `/chat/**`
  - `/message/**`
- Token refresh is automatic via `/auth/refresh`.
- Messages are sent through `POST /message`.

## Realtime notes

The gateway websocket endpoint `/message` requires an `Authorization` header during handshake.
Browser websocket API cannot attach custom headers directly, so this frontend uses a dev proxy route:

- client connects to `/ws-message?token=<accessToken>`
- Vite proxy rewrites to `/message` and injects `Authorization: Bearer <accessToken>`

If you deploy outside Vite dev server, configure your reverse proxy similarly or adjust backend auth strategy for websocket handshakes.
Point websocket clients to a proxy endpoint like `/ws-message`, not directly to `/message`.

## Optional env vars

- `VITE_API_BASE_URL` (default empty, so browser uses same origin/proxy)
- `VITE_WS_URL` (explicit websocket endpoint, for example `wss://example.com/ws-message`; do not use `/message` directly)
- `VITE_WS_PATH` (default `/ws-message`)

## Product limitations inherited from backend

- No endpoint to list all chats for current user.
- No endpoint to fetch message history for a chat.
- `POST /chat` returns only `"success"`, not the created `chatId`.

To stay usable, frontend keeps local chat/message context in browser storage and supports manual open by `chatId`.
