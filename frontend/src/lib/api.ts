import type {
  ChatInfo,
    ChatParticipantDto,
    ChatSlice,
    EditProfilePayload,
    LoginPayload,
    MessageHistoryItem,
    MessageSlice,
    Reaction,
    RegisterPayload,
    TokenPair,
  UserProfile
} from "../types";

type ApiConfig = {
  getTokens: () => TokenPair | null;
  setTokens: (tokens: TokenPair | null) => void;
  onAuthLost: () => void;
};

type RequestOptions = {
  auth?: boolean;
  noRefresh?: boolean;
};

export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function ensureLeadingSlash(path: string): string {
  return path.startsWith("/") ? path : `/${path}`;
}

function decodeJwtUserId(token: string): number {
  const [, payloadPart] = token.split(".");
  if (!payloadPart) {
    throw new ApiError(500, "Invalid access token format.");
  }

  const base64 = payloadPart.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);

  let payloadText: string;
  try {
    payloadText = atob(padded);
  } catch {
    throw new ApiError(500, "Failed to decode access token payload.");
  }

  let payload: unknown;
  try {
    payload = JSON.parse(payloadText);
  } catch {
    throw new ApiError(500, "Invalid access token payload.");
  }

  if (!payload || typeof payload !== "object") {
    throw new ApiError(500, "Invalid access token payload.");
  }

  const subject = (payload as Record<string, unknown>).sub;
  const userId = typeof subject === "string" ? Number.parseInt(subject, 10) : Number.NaN;
  if (!Number.isInteger(userId) || userId <= 0) {
    throw new ApiError(500, "Unable to resolve user id from access token.");
  }

  return userId;
}

function toErrorMessage(payload: unknown, fallback: string): string {
  if (typeof payload === "string" && payload.trim()) {
    return payload;
  }

  if (payload && typeof payload === "object") {
    const dictionary = payload as Record<string, unknown>;

    for (const key of ["message", "error", "detail"]) {
      const value = dictionary[key];
      if (typeof value === "string" && value.trim()) {
        return value;
      }
    }
  }

  return fallback;
}

async function parsePayload(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    try {
      return await response.json();
    } catch {
      return null;
    }
  }

  const text = await response.text();
  return text;
}

export function createApiClient(config: ApiConfig) {
  async function refreshAccessToken(): Promise<TokenPair | null> {
    const current = config.getTokens();
    if (!current) {
      return null;
    }

    const headers = new Headers();
    headers.set("Content-Type", "application/json");
    headers.set("Authorization", `Bearer ${current.token}`);

    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers,
      body: JSON.stringify({ refreshToken: current.refreshToken })
    });

    if (!response.ok) {
      return null;
    }

    const payload = await parsePayload(response);
    if (!payload || typeof payload !== "object") {
      return null;
    }

    const next = payload as Partial<TokenPair>;
    if (!next.token || !next.refreshToken) {
      return null;
    }

    const pair: TokenPair = {
      token: next.token,
      refreshToken: next.refreshToken
    };
    config.setTokens(pair);
    return pair;
  }

  async function requestRaw(
    path: string,
    init: RequestInit = {},
    options: RequestOptions = {}
  ): Promise<{ response: Response; payload: unknown }> {
    const useAuth = options.auth !== false;
    const headers = new Headers(init.headers);

    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    if (useAuth) {
      const token = config.getTokens()?.token;
      if (!token) {
        config.onAuthLost();
        throw new ApiError(401, "Authentication required.");
      }

      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await fetch(`${API_BASE}${ensureLeadingSlash(path)}`, {
      cache: "no-store",
      ...init,
      headers
    });

    if (response.status === 401 && useAuth && !options.noRefresh) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return requestRaw(path, init, { ...options, noRefresh: true });
      }

      config.setTokens(null);
      config.onAuthLost();
      throw new ApiError(401, "Session expired. Please sign in again.");
    }

    const payload = await parsePayload(response);
    if (!response.ok) {
      throw new ApiError(
        response.status,
        toErrorMessage(payload, `Request failed with status ${response.status}`)
      );
    }

    return {
      response,
      payload
    };
  }

  async function request<T>(
    path: string,
    init: RequestInit = {},
    options: RequestOptions = {}
  ): Promise<T> {
    const result = await requestRaw(path, init, options);
    return result.payload as T;
  }

  return {
    login: (dto: LoginPayload) => request<TokenPair>("/auth/login", {
      method: "POST",
      body: JSON.stringify(dto)
    }, { auth: false }),

    register: (dto: RegisterPayload) => request<TokenPair>("/auth/register", {
      method: "POST",
      body: JSON.stringify(dto)
    }, { auth: false }),

    getMyUserId: async () => {
      const { response } = await requestRaw("/auth/validate", { method: "GET" });
      const userIdHeader = response.headers.get("X-User-Id") ?? response.headers.get("x-user-id");
      if (userIdHeader) {
        const userIdFromHeader = Number.parseInt(userIdHeader, 10);
        if (Number.isInteger(userIdFromHeader) && userIdFromHeader > 0) {
          return userIdFromHeader;
        }
      }

      const token = config.getTokens()?.token;
      if (!token) {
        config.onAuthLost();
        throw new ApiError(401, "Authentication required.");
      }
      return decodeJwtUserId(token);
    },

    getMyProfile: () => request<UserProfile>("/user/me", { method: "GET" }),

    getUserById: (userId: number) => request<UserProfile>(`/user/${userId}`, { method: "GET" }),

    searchUsersByTag: (tagQuery: string) =>
      request<UserProfile[]>(`/user/search?tag=${encodeURIComponent(tagQuery)}`, { method: "GET" }),

    editMyProfile: (dto: EditProfilePayload) => request<UserProfile>("/user/edit", {
      method: "POST",
      body: JSON.stringify(dto)
    }),

    deleteMyProfile: () => request<void>("/user", { method: "DELETE" }),

    updateAvatar: (newUrl: string | null) => request<UserProfile>("/user/edit/avatar", {
      method: "PUT",
      body: JSON.stringify({ newUrl })
    }),

    createChat: (peerUserId: number, chatName: string) => request<ChatInfo>("/chat", {
      method: "POST",
      body: JSON.stringify({ name: chatName, chatType: "PRIVATE", participantIds: [peerUserId] })
    }),

    createGroupChat: (name: string, participantIds: number[]) => request<ChatInfo>("/chat", {
      method: "POST",
      body: JSON.stringify({ name, chatType: "GROUP", participantIds })
    }),

    chatHasUser: (chatId: number, userId: number) =>
      request<boolean>(`/chat/${chatId}/users/${userId}/exists`, { method: "GET" }),

    getChats: (limit = 50, offset = 0) =>
      request<ChatSlice>(`/chat?limit=${limit}&offset=${offset}`, { method: "GET" }),

    getChatUsers: (chatId: number) =>
      request<ChatParticipantDto[]>(`/chat/${chatId}/users`, { method: "GET" }),

    addUserToChat: (chatId: number, userId: number) => request<void>("/chat/users", {
      method: "POST",
      body: JSON.stringify({ chatId, userId, role: "MEMBER" })
    }),

    updateChatUserRole: (chatId: number, userId: number, role: "OWNER" | "ADMIN" | "MEMBER") => request<void>("/chat/role", {
      method: "PUT",
      body: JSON.stringify({ chatId, userId, role })
    }),

    changeChatName: (chatId: number, newName: string) => request<void>(`/chat/${chatId}/name/${encodeURIComponent(newName)}`, {
      method: "PUT"
    }),

    updateChatAvatar: (chatId: number, avatarUrl: string | null) => request<void>(`/chat/${chatId}/avatar`, {
      method: "PUT",
      body: JSON.stringify({ avatarUrl })
    }),

    setMyCustomChatName: (chatId: number, name: string | null) => request<void>(`/chat/${chatId}/my-name`, {
      method: "PUT",
      body: JSON.stringify({ name })
    }),

    deleteChat: (chatId: number) => request<void>(`/chat/${chatId}`, { method: "DELETE" }),

    leaveChat: (chatId: number) => request<void>(`/chat/leave/${chatId}`, { method: "DELETE" }),

    getMessages: (chatId: number, limit = 30, offset = 0) =>
      request<MessageSlice>(`/message/chat/${chatId}?limit=${limit}&offset=${offset}`, { method: "GET" }),

    readMessage: (messageId: string | number) =>
      request<void>(`/message/read/${encodeURIComponent(String(messageId))}`, { method: "PUT" }),

    readMessages: (messageIds: Array<string | number>) =>
      request<void>("/message/read", {
        method: "PUT",
        body: JSON.stringify({ ids: messageIds.map((id) => String(id)) })
      }),

    deleteMessage: (messageId: string | number) =>
      request<void>(`/message/${encodeURIComponent(String(messageId))}`, { method: "DELETE" }),

    editMessage: (messageId: string | number, content: string) =>
      request<MessageHistoryItem>("/message/edit", {
        method: "PUT",
        body: JSON.stringify({ id: String(messageId), content })
      }),

    sendMessage: (chatId: number, content: string, repliedMessageId?: string | null, photoLinks?: string[] | null) =>
      request<MessageHistoryItem>("/message", {
        method: "POST",
        body: JSON.stringify({
          chatId,
          content,
          ...(repliedMessageId ? { repliedMessageId } : {}),
          ...(photoLinks && photoLinks.length > 0 ? { photoLinks } : {})
        })
      }),

    uploadMedia: (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      return request<{ url: string; filename: string }>("/media/upload", {
        method: "POST",
        body: formData
      });
    },

    getReactions: (messageId: string) =>
      request<Reaction[]>(`/reaction/message/${encodeURIComponent(messageId)}`, { method: "GET" }),

    batchGetReactions: (userId: number, messageIds: string[]) =>
      request<{ reactions: Record<string, Reaction[]> }>("/reaction/message/batchByUser", {
        method: "POST",
        body: JSON.stringify({ userId, messageIds })
      }),

    addReaction: (chatId: number, messageId: string, reactionType: string) =>
      request<unknown>("/reaction/add", {
        method: "POST",
        body: JSON.stringify({ chatId, messageId, reactionType })
      }),

    deleteReaction: (messageId: string, reactionType: string) =>
      request<void>(`/reaction/message/${encodeURIComponent(messageId)}/${encodeURIComponent(reactionType)}`, { method: "DELETE" }),

    fetchMediaBlob: async (path: string, onProgress?: (progress: number) => void): Promise<string> => {
      const doFetch = async (retried = false): Promise<Response> => {
        const token = config.getTokens()?.token;
        const headers = new Headers();
        if (token) headers.set("Authorization", `Bearer ${token}`);
        const res = await fetch(`${API_BASE}${ensureLeadingSlash(path)}`, { headers });
        if (res.status === 401 && !retried) {
          const refreshed = await refreshAccessToken();
          if (refreshed) return doFetch(true);
          config.setTokens(null);
          config.onAuthLost();
          throw new ApiError(401, "Session expired.");
        }
        return res;
      };
      const response = await doFetch();
      if (!response.ok) {
        const body = await response.text().catch(() => "");
        throw new ApiError(response.status, `Failed to load media: HTTP ${response.status} — ${body}`);
      }
      const total = parseInt(response.headers.get("Content-Length") ?? "0", 10);
      if (onProgress && total > 0 && response.body) {
        const reader = response.body.getReader();
        const chunks: Uint8Array[] = [];
        let loaded = 0;
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          chunks.push(value);
          loaded += value.length;
          onProgress(loaded / total);
        }
        const merged = new Uint8Array(loaded);
        let offset = 0;
        for (const chunk of chunks) { merged.set(chunk, offset); offset += chunk.length; }
        const blob = new Blob([merged], { type: response.headers.get("Content-Type") ?? "image/jpeg" });
        return URL.createObjectURL(blob);
      }
      const blob = await response.blob();
      return URL.createObjectURL(blob);
    }
  };
}
