export type RealtimeStatus = "offline" | "connecting" | "online" | "reconnecting" | "error";

export type SocketMessageEvent = {
  type: "message";
  id: string;
  chatId: number;
  userId: number;
  content: string | null;
  editStatus?: boolean;
  sendAt?: string;
  photoLinks?: string[] | null;
};

export type SocketMessageReadEvent = {
  type: "message_read";
  id: string;
  chatId: number;
  readerId: number;
  readStatus: boolean;
};

export type SocketMessageDeleteEvent = {
  type: "message_delete";
  id: string;
  chatId: number;
  deletedByUserId: number;
};

export type SocketMessageEditEvent = {
  type: "message_edit";
  id: string;
  chatId: number;
  content: string;
  editStatus?: boolean;
};

export type SocketPresenceEvent = {
  type: "presence";
  userId: number;
  online: boolean;
};

export type SocketReactionEvent = {
  type: "reaction_added" | "reaction_deleted";
  chatId: number;
  messageId: string;
  userId: number;
  reactionType: string;
};

export type SocketTypingEvent = {
  type: "typing";
  chatId: number;
  userId: number;
};

type RealtimeHandlers = {
  onMessage: (event: SocketMessageEvent) => void;
  onMessageRead: (event: SocketMessageReadEvent) => void;
  onMessageDelete: (event: SocketMessageDeleteEvent) => void;
  onMessageEdit: (event: SocketMessageEditEvent) => void;
  onPresence: (event: SocketPresenceEvent) => void;
  onReaction: (event: SocketReactionEvent) => void;
  onTyping: (event: SocketTypingEvent) => void;
  onStatus: (status: RealtimeStatus) => void;
  onError: (message: string) => void;
};

function normalizeWsBase(raw: string): string {
  if (raw.startsWith("ws://") || raw.startsWith("wss://")) {
    return raw;
  }

  if (raw.startsWith("http://")) {
    return `ws://${raw.slice("http://".length)}`;
  }

  if (raw.startsWith("https://")) {
    return `wss://${raw.slice("https://".length)}`;
  }

  const wsOrigin = window.location.origin.startsWith("https")
    ? window.location.origin.replace(/^https/, "wss")
    : window.location.origin.replace(/^http/, "ws");

  if (raw.startsWith("/")) {
    return `${wsOrigin}${raw}`;
  }

  return new URL(raw, `${wsOrigin}/`).toString();
}

function buildSocketUrl(token: string): string {
  const configuredRaw = import.meta.env.VITE_WS_URL;
  const configured = typeof configuredRaw === "string" ? configuredRaw.trim() : "";
  const fallbackPath = import.meta.env.VITE_WS_PATH ?? "/ws-message";

  const base = configured ? normalizeWsBase(configured) : normalizeWsBase(fallbackPath);
  const url = new URL(base);
  if (url.pathname === "/message") {
    throw new Error("WebSocket URL '/message' is not supported from browser directly. Use '/ws-message' via reverse proxy.");
  }

  url.searchParams.set("token", token);
  return url.toString();
}

function isSocketMessageEvent(payload: unknown): payload is SocketMessageEvent {
  if (!payload || typeof payload !== "object") {
    return false;
  }

  const value = payload as Record<string, unknown>;
  return (
    value.type === "message" &&
    typeof value.id === "string" &&
    typeof value.chatId === "number" &&
    typeof value.userId === "number" &&
    (typeof value.content === "string" || value.content == null) &&
    (value.editStatus == null || typeof value.editStatus === "boolean") &&
    (value.sendAt == null || typeof value.sendAt === "string")
  );
}

function isSocketMessageReadEvent(payload: unknown): payload is SocketMessageReadEvent {
  if (!payload || typeof payload !== "object") {
    return false;
  }

  const value = payload as Record<string, unknown>;
  return (
    value.type === "message_read" &&
    typeof value.id === "string" &&
    typeof value.chatId === "number" &&
    typeof value.readerId === "number" &&
    typeof value.readStatus === "boolean"
  );
}

function isSocketMessageDeleteEvent(payload: unknown): payload is SocketMessageDeleteEvent {
  if (!payload || typeof payload !== "object") {
    return false;
  }

  const value = payload as Record<string, unknown>;
  return (
    value.type === "message_delete" &&
    typeof value.id === "string" &&
    typeof value.chatId === "number" &&
    typeof value.deletedByUserId === "number"
  );
}

function isSocketMessageEditEvent(payload: unknown): payload is SocketMessageEditEvent {
  if (!payload || typeof payload !== "object") {
    return false;
  }

  const value = payload as Record<string, unknown>;
  return (
    value.type === "message_edit" &&
    typeof value.id === "string" &&
    typeof value.chatId === "number" &&
    typeof value.content === "string" &&
    (value.editStatus == null || typeof value.editStatus === "boolean")
  );
}

function isSocketPresenceEvent(payload: unknown): payload is SocketPresenceEvent {
  if (!payload || typeof payload !== "object") {
    return false;
  }

  const value = payload as Record<string, unknown>;
  return (
    value.type === "presence" &&
    typeof value.userId === "number" &&
    typeof value.online === "boolean"
  );
}

function isSocketReactionEvent(payload: unknown): payload is SocketReactionEvent {
  if (!payload || typeof payload !== "object") {
    return false;
  }

  const value = payload as Record<string, unknown>;
  return (
    (value.type === "reaction_added" || value.type === "reaction_deleted") &&
    typeof value.chatId === "number" &&
    typeof value.messageId === "string" &&
    typeof value.userId === "number" &&
    typeof value.reactionType === "string"
  );
}

function isSocketTypingEvent(payload: unknown): payload is SocketTypingEvent {
  if (!payload || typeof payload !== "object") return false;
  const value = payload as Record<string, unknown>;
  return (
    value.type === "typing" &&
    typeof value.chatId === "number" &&
    typeof value.userId === "number"
  );
}

export class RealtimeBridge {
  private socket: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private reconnectAttempts = 0;
  private closedByClient = false;
  private presenceTargets: number[] = [];

  constructor(
    private readonly getToken: () => string | null | Promise<string | null>,
    private readonly handlers: RealtimeHandlers
  ) {}

  connect(): void {
    this.closedByClient = false;
    this.clearReconnectTimer();

    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    void this.open();
  }

  setPresenceTargets(userIds: number[]): void {
    const normalized = Array.from(
      new Set(
        userIds
          .filter((candidate) => Number.isInteger(candidate) && candidate > 0)
          .map((candidate) => Math.trunc(candidate))
      )
    ).sort((left, right) => left - right);

    const sameLength = normalized.length === this.presenceTargets.length;
    const unchanged = sameLength && normalized.every((value, index) => value === this.presenceTargets[index]);
    if (unchanged) {
      return;
    }

    this.presenceTargets = normalized;
    this.sendPresenceSubscription();
  }

  disconnect(): void {
    this.closedByClient = true;
    this.clearReconnectTimer();

    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }

    this.handlers.onStatus("offline");
  }

  private async open(): Promise<void> {
    let token: string | null;
    try {
      token = await this.getToken();
    } catch {
      this.handlers.onStatus("error");
      this.handlers.onError("Failed to validate realtime session.");
      if (!this.closedByClient) {
        this.scheduleReconnect();
      }
      return;
    }

    if (this.closedByClient) {
      return;
    }

    if (!token) {
      this.handlers.onStatus("offline");
      return;
    }

    this.handlers.onStatus(this.reconnectAttempts > 0 ? "reconnecting" : "connecting");
    this.clearReconnectTimer();

    let socket: WebSocket;
    try {
      const socketUrl = buildSocketUrl(token);
      socket = new WebSocket(socketUrl);
    } catch (error) {
      this.handlers.onStatus("error");
      this.handlers.onError(error instanceof Error ? error.message : "Failed to open websocket connection.");
      return;
    }

    this.socket = socket;
    let reconnectRequested = false;

    const requestReconnect = () => {
      if (reconnectRequested || this.closedByClient) {
        return;
      }

      reconnectRequested = true;

      // Force-close the current transport to avoid stale half-open sockets.
      if (this.socket === socket) {
        this.socket = null;
      }
      try {
        socket.close();
      } catch {
        // Ignore close errors and continue with reconnect scheduling.
      }

      this.scheduleReconnect();
    };

    socket.onopen = () => {
      if (this.socket !== socket) {
        return;
      }

      this.reconnectAttempts = 0;
      this.handlers.onStatus("online");
      this.sendPresenceSubscription();
    };

    socket.onmessage = (event) => {
      if (this.socket !== socket) {
        return;
      }

      try {
        const payload = JSON.parse(event.data) as unknown;

        if (isSocketMessageEvent(payload)) {
          this.handlers.onMessage(payload);
          return;
        }

        if (isSocketMessageReadEvent(payload)) {
          this.handlers.onMessageRead(payload);
          return;
        }

        if (isSocketMessageDeleteEvent(payload)) {
          this.handlers.onMessageDelete(payload);
          return;
        }

        if (isSocketMessageEditEvent(payload)) {
          this.handlers.onMessageEdit(payload);
          return;
        }

        if (isSocketPresenceEvent(payload)) {
          this.handlers.onPresence(payload);
          return;
        }

        if (isSocketReactionEvent(payload)) {
          this.handlers.onReaction(payload);
          return;
        }

        if (isSocketTypingEvent(payload)) {
          this.handlers.onTyping(payload);
          return;
        }

        if (payload && typeof payload === "object") {
          const dictionary = payload as Record<string, unknown>;
          if (dictionary.status === "error" && typeof dictionary.message === "string") {
            this.handlers.onError(dictionary.message);
          }
        }
      } catch {
        this.handlers.onError("Failed to parse websocket payload.");
      }
    };

    socket.onerror = () => {
      if (this.socket !== socket) {
        return;
      }

      this.handlers.onStatus("error");

      // In some environments `error` can happen without a guaranteed `close` event.
      // Trigger reconnect here as a fallback to avoid hanging in error state.
      requestReconnect();
    };

    socket.onclose = () => {
      if (this.socket === socket) {
        this.socket = null;
      }
      if (this.closedByClient) {
        return;
      }

      requestReconnect();
    };
  }

  private scheduleReconnect(): void {
    this.reconnectAttempts += 1;
    this.handlers.onStatus("reconnecting");

    const timeout = Math.min(10_000, 800 * 2 ** Math.min(this.reconnectAttempts, 5));
    this.clearReconnectTimer();

    this.reconnectTimer = window.setTimeout(() => {
      void this.open();
    }, timeout);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  sendTyping(chatId: number): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    try {
      this.socket.send(JSON.stringify({ type: "typing", chatId }));
    } catch {
      // ignore
    }
  }

  private sendPresenceSubscription(): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }

    try {
      this.socket.send(JSON.stringify({
        type: "presence_subscribe",
        userIds: this.presenceTargets
      }));
    } catch {
      // Ignore temporary socket send errors, next reconnect/subscription update will retry.
    }
  }
}
