import type { TokenPair } from "../types";

const TOKENS_KEY = "messenger.tokens";

// Legacy keys written by older versions — clear on startup so stale data never shows.
const STALE_KEYS = ["messenger.chats", "messenger.messages"];

function safeRead<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") {
    return fallback;
  }

  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) {
      return fallback;
    }

    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function safeWrite<T>(key: string, value: T): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(key, JSON.stringify(value));
}

export function purgeLegacyStorage(): void {
  if (typeof window === "undefined") return;
  for (const key of STALE_KEYS) {
    window.localStorage.removeItem(key);
  }
}

export function loadTokens(): TokenPair | null {
  return safeRead<TokenPair | null>(TOKENS_KEY, null);
}

export function saveTokens(tokens: TokenPair | null): void {
  if (tokens === null) {
    if (typeof window !== "undefined") {
      window.localStorage.removeItem(TOKENS_KEY);
    }
    return;
  }

  safeWrite(TOKENS_KEY, tokens);
}
