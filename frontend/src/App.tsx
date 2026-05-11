import {
  type FormEvent,
  type MouseEvent as ReactMouseEvent,
  type TouchEvent as ReactTouchEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState
} from "react";
import { ApiError, createApiClient } from "./lib/api";

const mediaBlobCache = new Map<string, string>();
import {
  RealtimeBridge,
  type RealtimeStatus,
  type SocketMessageEvent,
  type SocketMessageEditEvent,
  type SocketMessageDeleteEvent,
  type SocketMessageReadEvent,
  type SocketPresenceEvent,
  type SocketReactionEvent,
  type SocketTypingEvent
} from "./lib/realtime";
import { loadTokens, purgeLegacyStorage, saveTokens } from "./lib/storage";
import type { ChatInfo, ChatMessage, ChatParticipantDto, KnownChat, MessageHistoryItem, Reaction, TokenPair, UserProfile } from "./types";
import { REACTION_EMOJIS } from "./types";

type AuthMode = "login" | "register";
type AppRoute =
  | "/login"
  | "/register"
  | "/"
  | "/search"
  | "/settings"
  | "/settings/profile"
  | "/settings/profile/edit"
  | "/settings/appearance"
  | "/profile";
type ThemeMode = "light" | "dark";

type ChatPagination = {
  initialized: boolean;
  loading: boolean;
  nextOffset: number;
  hasMore: boolean;
};

type TimelineRow =
  | { kind: "day"; key: string; label: string }
  | { kind: "message"; key: string; message: ChatMessage };

type MessageContextMenuState = {
  messageId: string;
  x: number;
  y: number;
};

type ChatContextMenuState = {
  chatId: number;
  x: number;
  y: number;
};

type LongPressTarget =
  | { kind: "chat"; chatId: number }
  | { kind: "message"; messageId: string };

type MessageEditDraft = {
  chatId: number;
  messageId: string;
};

const STATUS_LABELS: Record<RealtimeStatus, string> = {
  offline: "Не в сети",
  connecting: "Подключение",
  online: "В сети",
  reconnecting: "Переподключение",
  error: "Ошибка соединения"
};

const HISTORY_PAGE_SIZE = 30;
const CHAT_SYNC_PAGE_SIZE = 50;
const CHAT_SYNC_MAX_PAGES = 20;
const MESSAGE_LOOKUP_PAGE_SIZE = 50;
const MESSAGE_LOOKUP_MAX_PAGES = 100;
const READ_SYNC_BATCH_SIZE = 500;
const UNREAD_SCAN_PAGE_SIZE = 50;
const UNREAD_SCAN_MAX_PAGES = 20;
const UNREAD_SCAN_CONCURRENCY = 4;
const TOP_LOAD_THRESHOLD = 96;
const BOTTOM_AUTO_SCROLL_THRESHOLD = 40;
const LOCAL_ECHO_MATCH_WINDOW_MS = 15_000;
const WS_REMOTE_DUPLICATE_WINDOW_MS = 1_500;
const CONTEXT_LONG_PRESS_MS = 440;
const CONTEXT_LONG_PRESS_MOVE_PX = 12;
const USER_SEARCH_DEBOUNCE_MS = 360;
const THEME_STORAGE_KEY = "messenger-theme";
const PRESENCE_OFFLINE_DELAY_MS = 2000;
const PRESENCE_CACHE_KEY = "messenger-presence-cache";
const TYPING_EXPIRE_MS = 2500;
const TYPING_DEBOUNCE_MS = 800;

const URL_REGEX = /https?:\/\/(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b[-a-zA-Z0-9()@:%_+.~#?&/=]*/g;

const EMOJI_CATEGORIES = [
  { icon: "😀", name: "Смайлы", emojis: ["😀","😃","😄","😁","😆","😅","🤣","😂","🙂","😉","😊","😍","🥰","😘","😋","😛","😜","🤪","🤑","🤗","🤔","🤫","🤐","😐","😑","🙄","😬","😏","😒","😔","😴","😷","🥺","😢","😭","😤","😠","🤬","😈","👿","💀","💩","🤡","👻","👽","🤖"] },
  { icon: "👍", name: "Жесты", emojis: ["👍","👎","👊","✊","🤛","🤜","🤞","✌️","🤟","🤙","👈","👉","👆","☝️","🖐️","✋","🤚","🙌","👏","🤲","🙏","💪","🦾","❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","💕","💯","🔥","⭐","💫","✨","💥","💦","🌈"] },
  { icon: "🐶", name: "Природа", emojis: ["🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🐔","🐧","🦆","🦉","🦋","🐝","🐢","🐍","🦎","🦖","🦕","🌸","🌺","🌻","🌹","🌿","🍀","🌈","☀️","🌊","🌋","🏔️","🌍"] },
  { icon: "🍕", name: "Еда", emojis: ["🍕","🍔","🍟","🌮","🌯","🥗","🍣","🍱","🍜","🍝","🍛","🥘","🍲","🥙","🥚","🍳","🥞","🧇","🥓","🧀","🍞","🥐","🥑","🍓","🍎","🍊","🍋","🍇","🍉","🍒","🍍","🥭","🥝","☕","🍵","🧋","🥤","🍺","🍷","🎂","🍰","🍩","🍪","🍫","🍬","🍭"] },
  { icon: "⚽", name: "Игры", emojis: ["⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏓","🏸","🥊","🥋","⛳","🎳","🎯","🎮","🕹️","🎲","🎭","🎨","🎬","🎤","🎸","🎹","🎺","🎻","🎧","📸","🏆","🥇","🎀","🎁","🎊","🎉","🎈","🎇","🎆","🪅","🎠","🎡","🎢"] },
  { icon: "🚀", name: "Символы", emojis: ["❗","❓","‼️","⁉️","💯","🔔","🔕","💬","💭","💤","🚫","⛔","❌","✅","☑️","✔️","🔝","🆕","🆓","⬆️","⬇️","↩️","↪️","🔄","▶️","⏸️","⏹️","⏩","⏪","🔀","🔁","🔂","⏱️","⏰","📅","📆","📌","📍","🔍","🔎","💡","🔑","🗝️","🔒","🔓","💎","🏠","🏢","✈️","🚗","🚀","🌐"] }
] as const;

function resolveInitialTheme(): ThemeMode {
  if (typeof window === "undefined") {
    return "light";
  }

  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "light" || stored === "dark") {
    return stored;
  }

  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function normalizeRoute(pathname: string): AppRoute {
  switch (pathname) {
    case "/login":
    case "/register":
    case "/":
    case "/search":
    case "/settings":
    case "/settings/profile":
    case "/settings/profile/edit":
    case "/settings/appearance":
    case "/profile":
      return pathname;
    case "/chat":
      return "/";
    default:
      return "/";
  }
}

function isPublicRoute(route: AppRoute): boolean {
  return route === "/login" || route === "/register";
}

function isSettingsRoute(route: AppRoute): boolean {
  return route === "/settings"
    || route === "/settings/profile"
    || route === "/settings/profile/edit"
    || route === "/settings/appearance";
}

function createDefaultPagination(): ChatPagination {
  return {
    initialized: false,
    loading: false,
    nextOffset: 0,
    hasMore: true
  };
}

function getChatPagination(snapshot: Record<string, ChatPagination>, key: string): ChatPagination {
  return snapshot[key] ?? createDefaultPagination();
}

function formatLocalDateTime(value: Date | number): string {
  const date = value instanceof Date ? value : new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  const seconds = String(date.getSeconds()).padStart(2, "0");
  const milliseconds = String(date.getMilliseconds()).padStart(3, "0");

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${milliseconds}`;
}

function compareChatUpdatedAtDesc(leftUpdatedAt: string, rightUpdatedAt: string): number {
  const leftTs = Date.parse(leftUpdatedAt);
  const rightTs = Date.parse(rightUpdatedAt);
  const leftHasTs = Number.isFinite(leftTs);
  const rightHasTs = Number.isFinite(rightTs);

  if (leftHasTs && rightHasTs && leftTs !== rightTs) {
    return rightTs - leftTs;
  }

  if (leftHasTs && !rightHasTs) {
    return -1;
  }

  if (!leftHasTs && rightHasTs) {
    return 1;
  }

  return rightUpdatedAt.localeCompare(leftUpdatedAt);
}

function sortChats(chats: KnownChat[]): KnownChat[] {
  return [...chats].sort((left, right) => compareChatUpdatedAtDesc(left.updatedAt, right.updatedAt));
}

function chooseLatestIso(currentIso: string, candidateIso?: string): string {
  if (!candidateIso) {
    return currentIso;
  }

  const currentTs = Date.parse(currentIso);
  const candidateTs = Date.parse(candidateIso);
  const currentHasTs = Number.isFinite(currentTs);
  const candidateHasTs = Number.isFinite(candidateTs);

  if (currentHasTs && candidateHasTs) {
    return candidateTs > currentTs ? candidateIso : currentIso;
  }

  if (!currentHasTs && candidateHasTs) {
    return candidateIso;
  }

  if (currentHasTs && !candidateHasTs) {
    return currentIso;
  }

  return candidateIso.localeCompare(currentIso) > 0 ? candidateIso : currentIso;
}

function mapChatsFromServer(chats: ChatInfo[]): KnownChat[] {
  const fallbackUpdatedAt = "1970-01-01T00:00:00.000";

  return sortChats(chats.map((chat) => {
    const lastMessageAt = typeof chat.lastMessageAt === "string" && chat.lastMessageAt.trim()
      ? chat.lastMessageAt
      : fallbackUpdatedAt;

    return {
      chatId: chat.chatId,
      chatName: chat.chatName,
      peerUserId: null,
      updatedAt: lastMessageAt,
      lastMessagePreview: chat.lastMessagePreview ?? null,
      lastMessageUserId: chat.lastMessageUserId ?? null,
      lastMessageHasMedia: chat.lastMessageHasMedia ?? null,
      avatarUrl: chat.avatarUrl ?? null
    };
  }));
}

function makeLocalMessageId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function toMessageText(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return "Неожиданная ошибка.";
}

function messageKey(message: ChatMessage): string {
  const serverId = normalizeServerId(message.serverId);
  if (serverId !== null) {
    return `server:${serverId}`;
  }

  return `local:${message.id}`;
}

function normalizeServerId(value: ChatMessage["serverId"]): string | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return String(value);
  }

  if (typeof value === "string") {
    const normalized = value.trim();
    if (normalized) {
      return normalized;
    }
  }

  return null;
}

function deliveryLabel(delivery: ChatMessage["delivery"]): string {
  if (delivery === "pending") {
    return "Отправляется";
  }

  if (delivery === "failed") {
    return "Ошибка отправки";
  }

  if (delivery === "read") {
    return "Прочитано";
  }

  return "Отправлено";
}

function hasServerId(message: ChatMessage): boolean {
  return normalizeServerId(message.serverId) !== null;
}

function compareServerIds(left: string, right: string): number {
  const leftNumber = Number(left);
  const rightNumber = Number(right);
  const leftIsInteger = Number.isSafeInteger(leftNumber) && String(leftNumber) === left;
  const rightIsInteger = Number.isSafeInteger(rightNumber) && String(rightNumber) === right;

  if (leftIsInteger && rightIsInteger && leftNumber !== rightNumber) {
    return leftNumber - rightNumber;
  }

  return left.localeCompare(right);
}

function compareMessages(left: ChatMessage, right: ChatMessage): number {
  const leftTimestamp = left.createdAt ? Date.parse(left.createdAt) : Number.NaN;
  const rightTimestamp = right.createdAt ? Date.parse(right.createdAt) : Number.NaN;
  const leftHasTimestamp = Number.isFinite(leftTimestamp);
  const rightHasTimestamp = Number.isFinite(rightTimestamp);

  if (leftHasTimestamp && rightHasTimestamp && leftTimestamp !== rightTimestamp) {
    return leftTimestamp - rightTimestamp;
  }

  if (leftHasTimestamp && !rightHasTimestamp) {
    return 1;
  }

  if (!leftHasTimestamp && rightHasTimestamp) {
    return -1;
  }

  const leftServerId = normalizeServerId(left.serverId);
  const rightServerId = normalizeServerId(right.serverId);

  if (leftServerId !== null && rightServerId !== null && leftServerId !== rightServerId) {
    return compareServerIds(leftServerId, rightServerId);
  }

  if (leftServerId !== null) {
    return -1;
  }

  if (rightServerId !== null) {
    return 1;
  }

  return left.id.localeCompare(right.id);
}

function mergeChatMessages(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const merged = new Map<string, ChatMessage>();

  const deliveryWeight = (delivery: ChatMessage["delivery"]): number => {
    if (delivery === "read") {
      return 3;
    }

    if (delivery === "sent") {
      return 2;
    }

    if (delivery === "failed") {
      return 1;
    }

    return 0;
  };

  for (const message of [...current, ...incoming]) {
    const key = messageKey(message);
    const existing = merged.get(key);
    if (!existing) {
      merged.set(key, message);
      continue;
    }

    merged.set(key, {
      ...existing,
      ...message,
      // Never downgrade delivery state (e.g. read -> sent) during history merges.
      delivery: deliveryWeight(existing.delivery) >= deliveryWeight(message.delivery)
        ? existing.delivery
        : message.delivery,
      edited: Boolean(existing.edited) || Boolean(message.edited)
    });
  }

  return Array.from(merged.values()).sort(compareMessages);
}

function parseMessageTime(createdAt: string | null): number | null {
  if (!createdAt) {
    return null;
  }

  const timestamp = Date.parse(createdAt);
  return Number.isFinite(timestamp) ? timestamp : null;
}


function formatDbMessageTime(message: ChatMessage): string | null {
  if (!message.createdAt) {
    return null;
  }

  const timestamp = Date.parse(message.createdAt);
  if (!Number.isFinite(timestamp)) {
    return null;
  }

  return new Date(timestamp).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit"
  });
}

function dayKeyFromCreatedAt(createdAt: string | null): string {
  if (!createdAt) {
    return "unknown";
  }

  const timestamp = Date.parse(createdAt);
  if (!Number.isFinite(timestamp)) {
    return "unknown";
  }

  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatDayLabel(dayKey: string): string {
  if (dayKey === "unknown") {
    return "Без даты";
  }

  const timestamp = Date.parse(`${dayKey}T00:00:00`);
  if (!Number.isFinite(timestamp)) {
    return "Без даты";
  }

  return new Date(timestamp).toLocaleDateString("ru-RU", {
    day: "numeric",
    month: "long",
    year: "numeric"
  });
}

function renderDeliveryIcon(delivery: ChatMessage["delivery"]) {
  if (delivery === "pending") {
    return (
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <circle cx="8" cy="8" r="6.25" fill="none" stroke="currentColor" strokeWidth="1.5" />
        <path d="M8 4.7V8.2L10.6 9.6" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      </svg>
    );
  }

  if (delivery === "failed") {
    return (
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <circle cx="8" cy="8" r="6.2" fill="none" stroke="currentColor" strokeWidth="1.4" />
        <path d="M8 4.4V8.9" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
        <circle cx="8" cy="11.5" r="0.9" fill="currentColor" />
      </svg>
    );
  }

  if (delivery === "sent") {
    return (
      <svg viewBox="0 0 12 12" aria-hidden="true">
        <path
          d="M1.4 6L4.4 9L10.2 2.8"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 18 14" aria-hidden="true">
      <path
        d="M1.4 7.2L4.5 10.2L9 3.8"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M6.1 7.2L9.2 10.2L13.8 3.8"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

const PROGRESS_R = 13;
const PROGRESS_CIRC = 2 * Math.PI * PROGRESS_R;

function AttachmentView({ url, mine, fetchMedia }: {
  url: string;
  mine: boolean;
  fetchMedia: (path: string, onProgress?: (p: number) => void) => Promise<string>;
}) {
  const [blobSrc, setBlobSrc] = useState<string | null>(() => mediaBlobCache.get(url) ?? null);
  const [failed, setFailed] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const placeholderRef = useRef<HTMLDivElement>(null);

  const isUploading = url.startsWith("blob:");

  useEffect(() => {
    if (isUploading || blobSrc || failed || notFound) return;
    const cached = mediaBlobCache.get(url);
    if (cached) { setBlobSrc(cached); return; }

    const el = placeholderRef.current;
    if (!el) return;

    let cancelled = false;
    const path = (url.startsWith("http://") || url.startsWith("https://"))
      ? `/media/proxy?publicUrl=${encodeURIComponent(url)}`
      : `/media/download/${url}`;

    const startFetch = () => {
      fetchMedia(path, (p) => { if (!cancelled) setProgress(p); })
        .then(u => {
          if (cancelled) { URL.revokeObjectURL(u); return; }
          mediaBlobCache.set(url, u);
          setBlobSrc(u);
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            if ((err as { status?: number })?.status === 404) setNotFound(true);
            else setFailed(true);
          }
        });
    };

    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      observer.disconnect();
      startFetch();
    }, { rootMargin: "300px" });

    observer.observe(el);
    return () => { cancelled = true; observer.disconnect(); };
  }, [url, isUploading, blobSrc, failed, notFound, fetchMedia]);

  const src = isUploading ? url : blobSrc;
  const isLoading = !isUploading && !blobSrc && !failed && !notFound;

  if (notFound) {
    return (
      <div className={`tg-attachment-not-found${mine ? " mine" : ""}`}>
        <svg viewBox="0 0 24 24" aria-hidden="true" className="tg-attachment-not-found-icon">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="3" y1="3" x2="21" y2="21"/>
          <path d="M8.5 8.5a2 2 0 0 0 0 7h7a2 2 0 0 0 1.4-3.4"/>
        </svg>
        <span>Фото недоступно</span>
      </div>
    );
  }

  if (failed) {
    return (
      <div className={`tg-attachment-not-found${mine ? " mine" : ""}`}>
        <svg viewBox="0 0 24 24" aria-hidden="true" className="tg-attachment-not-found-icon">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="3" y1="3" x2="21" y2="21"/>
          <path d="M8.5 8.5a2 2 0 0 0 0 7h7a2 2 0 0 0 1.4-3.4"/>
        </svg>
        <span>Фото недоступно</span>
      </div>
    );
  }

  const showOverlay = isUploading || isLoading;

  const inner = (
    <>
      {src && <img src={src} alt="Фото" className="tg-message-image" />}
      {showOverlay && (
        <div className="tg-image-upload-overlay">
          <svg className={`tg-image-progress${progress === null ? " indeterminate" : ""}`} viewBox="0 0 32 32" aria-hidden="true">
            <circle className="tg-image-progress-bg" cx="16" cy="16" r={PROGRESS_R} />
            <circle
              className="tg-image-progress-fill"
              cx="16" cy="16" r={PROGRESS_R}
              style={{
                strokeDasharray: PROGRESS_CIRC,
                strokeDashoffset: progress !== null ? PROGRESS_CIRC * (1 - progress) : PROGRESS_CIRC * 0.72
              }}
            />
          </svg>
        </div>
      )}
    </>
  );

  if (showOverlay) {
    return <div ref={placeholderRef} className={`tg-message-image-wrap${isLoading ? " loading" : " uploading"}`}>{inner}</div>;
  }

  return (
    <a href={src ?? "#"} target="_blank" rel="noopener noreferrer" className="tg-message-image-wrap">
      {inner}
    </a>
  );
}

function AvatarImage({ name, seed, avatarUrl, size, fetchMedia, children }: {
  name: string;
  seed: number;
  avatarUrl?: string | null;
  size?: "xs" | "sm" | "md";
  fetchMedia: (path: string) => Promise<string>;
  children?: React.ReactNode;
}) {
  const [blobSrc, setBlobSrc] = useState<string | null>(() =>
    avatarUrl ? (mediaBlobCache.get(avatarUrl) ?? null) : null
  );
  const [loading, setLoading] = useState(() => Boolean(avatarUrl && !mediaBlobCache.get(avatarUrl)));
  const [imgFailed, setImgFailed] = useState(false);
  const color = avatarColor(seed);
  const initial = avatarInitial(name);
  const sizeClass = size === "sm" ? " tg-avatar-sm" : size === "xs" ? " tg-avatar-xs" : "";

  useEffect(() => {
    if (!avatarUrl) { setLoading(false); return; }
    const cached = mediaBlobCache.get(avatarUrl);
    if (cached) { setBlobSrc(cached); setLoading(false); return; }
    if (imgFailed) { setLoading(false); return; }

    setLoading(true);
    let cancelled = false;
    const path = (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))
      ? `/media/proxy?publicUrl=${encodeURIComponent(avatarUrl)}`
      : `/media/download/${avatarUrl}`;

    fetchMedia(path)
      .then((u) => {
        if (cancelled) { URL.revokeObjectURL(u); return; }
        mediaBlobCache.set(avatarUrl, u);
        setBlobSrc(u);
        setLoading(false);
      })
      .catch(() => {
        if (!cancelled) { setImgFailed(true); setLoading(false); }
      });

    return () => { cancelled = true; };
  }, [avatarUrl, imgFailed, fetchMedia]);

  const showImg = avatarUrl && blobSrc && !imgFailed;

  return (
    <div
      className={`tg-avatar${sizeClass}${loading ? " tg-avatar-loading" : ""}`}
      style={showImg ? {} : { background: color }}
      aria-hidden="true"
    >
      {showImg
        ? <img src={blobSrc} alt="" className="tg-avatar-img" />
        : (!loading && initial)}
      {loading && (
        <svg className="tg-avatar-spinner" viewBox="0 0 32 32" aria-hidden="true">
          <circle className="tg-avatar-spinner-track" cx="16" cy="16" r="12" />
          <circle className="tg-avatar-spinner-fill" cx="16" cy="16" r="12" />
        </svg>
      )}
      {children}
    </div>
  );
}

function extractUrls(text: string): string[] {
  const matches = text.match(URL_REGEX);
  return matches ? [...new Set(matches)] : [];
}

function MessageText({ content }: { content: string }) {
  const urls = extractUrls(content);
  const firstUrl = urls[0] ?? null;

  // Split text into parts: text segments and url segments
  const parts: Array<{ text: string; isUrl: boolean }> = [];
  let last = 0;
  for (const match of content.matchAll(new RegExp(URL_REGEX.source, "g"))) {
    const idx = match.index ?? 0;
    if (idx > last) parts.push({ text: content.slice(last, idx), isUrl: false });
    parts.push({ text: match[0], isUrl: true });
    last = idx + match[0].length;
  }
  if (last < content.length) parts.push({ text: content.slice(last), isUrl: false });
  if (parts.length === 0) parts.push({ text: content, isUrl: false });

  let domain = "";
  if (firstUrl) {
    try { domain = new URL(firstUrl).hostname.replace(/^www\./, ""); } catch { /* ignore */ }
  }

  return (
    <>
      <p>
        {parts.map((part, i) =>
          part.isUrl ? (
            <a key={i} href={part.text} target="_blank" rel="noopener noreferrer" className="tg-link" onClick={(e) => e.stopPropagation()}>
              {part.text}
            </a>
          ) : part.text
        )}
      </p>
      {firstUrl && domain && (
        <a href={firstUrl} target="_blank" rel="noopener noreferrer" className="tg-link-preview" onClick={(e) => e.stopPropagation()}>
          <img
            src={`https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=32`}
            alt=""
            className="tg-link-preview-icon"
            onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = "none"; }}
          />
          <div className="tg-link-preview-body">
            <span className="tg-link-preview-domain">{domain}</span>
            <span className="tg-link-preview-url">{firstUrl.length > 60 ? firstUrl.slice(0, 60) + "…" : firstUrl}</span>
          </div>
          <svg viewBox="0 0 24 24" aria-hidden="true" className="tg-link-preview-arrow">
            <path d="M5 12h14M12 5l7 7-7 7"/>
          </svg>
        </a>
      )}
    </>
  );
}

function pruneLocalEchoes(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const next = [...current];

  for (const message of incoming) {
    const matchIndex = next.findIndex((candidate) =>
      !hasServerId(candidate) &&
      candidate.userId === message.userId &&
      candidate.content === message.content &&
      candidate.delivery !== "failed"
    );

    if (matchIndex >= 0) {
      next.splice(matchIndex, 1);
    }
  }

  return next;
}

function fallbackHistoryMessageId(message: MessageHistoryItem): string {
  const createdAt = message.sendAt ?? message.createdAt ?? "unknown-time";
  return `history-${message.chatId}-${message.userId}-${createdAt}-${message.content}`;
}

function toHistoryChatMessage(message: MessageHistoryItem, myUserId: number): ChatMessage {
  const historyServerId = normalizeServerId(message.id ?? undefined);
  const historyMessageId = historyServerId !== null ? `server-${historyServerId}` : fallbackHistoryMessageId(message);
  const mine = message.userId === myUserId;

  return {
    id: historyMessageId,
    chatId: message.chatId,
    userId: message.userId,
    content: message.content,
    createdAt: message.sendAt ?? message.createdAt ?? null,
    serverId: historyServerId ?? undefined,
    edited: Boolean(message.editStatus),
    delivery: mine && Boolean(message.readStatus) ? "read" : "sent",
    origin: mine ? "local" : "remote",
    repliedMessage: message.repliedMessage ?? null,
    photoLinks: message.photoLinks ?? null
  };
}

function isNearBottom(element: HTMLDivElement): boolean {
  return element.scrollHeight - element.scrollTop - element.clientHeight <= BOTTOM_AUTO_SCROLL_THRESHOLD;
}

const AVATAR_PALETTE = [
  "#2196F3", "#26A69A", "#AB47BC", "#EF5350",
  "#FF7043", "#66BB6A", "#EC407A", "#5C6BC0",
  "#29B6F6", "#8D6E63", "#42A5F5", "#26C6DA"
];

function avatarColor(seed: number): string {
  return AVATAR_PALETTE[Math.abs(seed) % AVATAR_PALETTE.length];
}

function avatarInitial(name: string): string {
  for (const char of name.trim()) {
    if (/\S/.test(char)) return char.toUpperCase();
  }
  return "?";
}

function formatChatTimestamp(iso: string): string {
  const ts = Date.parse(iso);
  if (!Number.isFinite(ts)) return "";
  const d = new Date(ts);
  const now = new Date();
  const isToday = d.getFullYear() === now.getFullYear()
    && d.getMonth() === now.getMonth()
    && d.getDate() === now.getDate();
  if (isToday) {
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  return d.toLocaleDateString([], { day: "numeric", month: "short" });
}

function loadPresenceCache(): Record<number, boolean> {
  if (typeof window === "undefined") {
    return {};
  }

  const raw = window.sessionStorage.getItem(PRESENCE_CACHE_KEY);
  if (!raw) {
    return {};
  }

  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object") {
      return {};
    }

    const result: Record<number, boolean> = {};
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      const userId = Number.parseInt(key, 10);
      if (!Number.isInteger(userId) || userId <= 0 || typeof value !== "boolean") {
        continue;
      }
      result[userId] = value;
    }
    return result;
  } catch {
    return {};
  }
}

function savePresenceCache(snapshot: Record<number, boolean>): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.sessionStorage.setItem(PRESENCE_CACHE_KEY, JSON.stringify(snapshot));
  } catch {
    // Ignore storage errors (private mode/quota), runtime presence state still works.
  }
}

function App() {
  purgeLegacyStorage();

  const [themeMode, setThemeMode] = useState<ThemeMode>(() => resolveInitialTheme());
  const [tokensState, setTokensState] = useState<TokenPair | null>(() => loadTokens());
  const tokenRef = useRef<TokenPair | null>(tokensState);

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(Boolean(tokensState));
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileReloadTick, setProfileReloadTick] = useState(0);
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileDeleteBusy, setProfileDeleteBusy] = useState(false);

  const [authBusy, setAuthBusy] = useState(false);
  const [route, setRoute] = useState<AppRoute>(() => normalizeRoute(window.location.pathname));

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");

  const [registerEmail, setRegisterEmail] = useState("");
  const [registerPassword, setRegisterPassword] = useState("");
  const [registerName, setRegisterName] = useState("");
  const [registerTag, setRegisterTag] = useState("");
  const [registerDescription, setRegisterDescription] = useState("");

  const [profileNameDraft, setProfileNameDraft] = useState("");
  const [profileTagDraft, setProfileTagDraft] = useState("");
  const [profileDescriptionDraft, setProfileDescriptionDraft] = useState("");
  const [profileViewUserId, setProfileViewUserId] = useState<number | null>(null);
  const [profileViewData, setProfileViewData] = useState<UserProfile | null>(null);
  const [profileViewLoading, setProfileViewLoading] = useState(false);
  const [profileViewError, setProfileViewError] = useState<string | null>(null);
  const [profileViewReloadTick, setProfileViewReloadTick] = useState(0);

  const [knownChats, setKnownChats] = useState<KnownChat[]>([]);
  const [unreadByChatId, setUnreadByChatId] = useState<Record<number, number>>({});
  const [messagesByChat, setMessagesByChat] = useState<Record<string, ChatMessage[]>>({});
  const [paginationByChat, setPaginationByChat] = useState<Record<string, ChatPagination>>({});
  const [userNamesById, setUserNamesById] = useState<Record<number, string>>({});
  const [avatarUrlByUserId, setAvatarUrlByUserId] = useState<Record<number, string | null>>({});
  const paginationRef = useRef<Record<string, ChatPagination>>({});
  const userNamesRef = useRef<Record<number, string>>({});
  const loadingUserIdsRef = useRef<Set<number>>(new Set());

  const [activeChatId, setActiveChatId] = useState<number | null>(null);
  const [activeDraftPeerUserId, setActiveDraftPeerUserId] = useState<number | null>(null);

  const [newChatTagInput, setNewChatTagInput] = useState("");
  const [chatSearchResults, setChatSearchResults] = useState<UserProfile[]>([]);
  const [chatSearchBusy, setChatSearchBusy] = useState(false);
  const [chatSearchError, setChatSearchError] = useState<string | null>(null);
  const [createChatBusy, setCreateChatBusy] = useState(false);
  const [deleteMessageBusyById, setDeleteMessageBusyById] = useState<Record<string, boolean>>({});
  const [messageMenu, setMessageMenu] = useState<MessageContextMenuState | null>(null);
  const [chatMenu, setChatMenu] = useState<ChatContextMenuState | null>(null);

  const [composerValue, setComposerValue] = useState("");
  const [messageEditDraft, setMessageEditDraft] = useState<MessageEditDraft | null>(null);
  const [wsStatus, setWsStatus] = useState<RealtimeStatus>("offline");
  const [presenceByUserId, setPresenceByUserId] = useState<Record<number, boolean>>(() => loadPresenceCache());
  const [presencePendingOfflineByUserId, setPresencePendingOfflineByUserId] = useState<Record<number, boolean>>({});
  const [notice, setNotice] = useState("");
  const [reactionsByMessageId, setReactionsByMessageId] = useState<Record<string, Reaction[]>>({});
  const [myReactionsByMessageId, setMyReactionsByMessageId] = useState<Record<string, string[]>>({});

  // Chat type tracking (separate from KnownChat to avoid touching upsertChat call sites)
  const [chatTypeById, setChatTypeById] = useState<Record<number, "PRIVATE" | "GROUP">>({});

  // Group info panel
  const [groupInfoOpen, setGroupInfoOpen] = useState(false);
  const [activeChatParticipants, setActiveChatParticipants] = useState<ChatParticipantDto[]>([]);
  const [activeChatParticipantsLoading, setActiveChatParticipantsLoading] = useState(false);

  // Create group modal
  const [createGroupOpen, setCreateGroupOpen] = useState(false);
  const [groupNameDraft, setGroupNameDraft] = useState("");
  const [groupSearchTag, setGroupSearchTag] = useState("");
  const [groupSearchResults, setGroupSearchResults] = useState<UserProfile[]>([]);
  const [groupSearchBusy, setGroupSearchBusy] = useState(false);
  const [groupSelectedMembers, setGroupSelectedMembers] = useState<UserProfile[]>([]);
  const [groupCreateBusy, setGroupCreateBusy] = useState(false);

  // Add member modal
  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [addMemberTag, setAddMemberTag] = useState("");
  const [addMemberResults, setAddMemberResults] = useState<UserProfile[]>([]);
  const [addMemberSearchBusy, setAddMemberSearchBusy] = useState(false);
  const [addMemberBusy, setAddMemberBusy] = useState(false);

  // Chat rename (global — for group admins)
  const [chatRenameOpen, setChatRenameOpen] = useState(false);
  const [chatRenameDraft, setChatRenameDraft] = useState("");
  const [chatRenameBusy, setChatRenameBusy] = useState(false);

  // Personal chat rename (customChatName)
  const [myRenameOpen, setMyRenameOpen] = useState(false);
  const [myRenameChatId, setMyRenameChatId] = useState<number | null>(null);
  const [myRenameDraft, setMyRenameDraft] = useState("");
  const [myRenameBusy, setMyRenameBusy] = useState(false);

  // Reply to message
  const [replyToMessage, setReplyToMessage] = useState<ChatMessage | null>(null);

  // File attachments
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);
  const [isDraggingOver, setIsDraggingOver] = useState(false);
  const [filePreviewUrls, setFilePreviewUrls] = useState<(string | null)[]>([]);

  // Avatar upload
  const [avatarUploading, setAvatarUploading] = useState(false);
  const avatarFileInputRef = useRef<HTMLInputElement | null>(null);

  // Typing indicators
  const [typingByChatId, setTypingByChatId] = useState<Record<number, Record<number, number>>>({});
  const typingTimeoutsRef = useRef<Record<string, number>>({});
  const lastTypingSentRef = useRef<number>(0);

  // Forward message
  const [forwardModalMessage, setForwardModalMessage] = useState<ChatMessage | null>(null);

  // Emoji picker
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false);
  const [emojiCategory, setEmojiCategory] = useState(0);

  // Scroll position per chat
  const scrollPositionByChat = useRef<Record<number, number>>({});

  // Group chat avatar uploading
  const [groupAvatarUploading, setGroupAvatarUploading] = useState(false);
  const groupAvatarInputRef = useRef<HTMLInputElement | null>(null);

  // Leave chat busy
  const [leaveChatBusy, setLeaveChatBusy] = useState(false);

  const timelineRef = useRef<HTMLDivElement | null>(null);
  const composerTextareaRef = useRef<HTMLTextAreaElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const messageMenuRef = useRef<HTMLDivElement | null>(null);
  const chatMenuRef = useRef<HTMLDivElement | null>(null);
  const stickToBottomRef = useRef(true);
  const realtimeRef = useRef<RealtimeBridge | null>(null);
  const profileRef = useRef<UserProfile | null>(profile);
  const presenceByUserIdRef = useRef<Record<number, boolean>>(presenceByUserId);
  const routeRef = useRef<AppRoute>(route);
  const activeChatIdRef = useRef<number | null>(activeChatId);
  const previousRouteRef = useRef<AppRoute>(route);
  const wsMatchedLocalMessageIdsRef = useRef<Set<string>>(new Set());
  const readSyncedServerIdsByChatRef = useRef<Record<string, Set<string>>>({});
  const readSyncInFlightRef = useRef<Set<string>>(new Set());
  const chatSearchRequestIdRef = useRef(0);
  const unreadHydrationRequestIdRef = useRef(0);
  const presenceOfflineTimersRef = useRef<Record<number, number>>({});
  const longPressTimerRef = useRef<number | null>(null);
  const longPressPointRef = useRef<{ x: number; y: number } | null>(null);
  const longPressTargetRef = useRef<LongPressTarget | null>(null);
  const suppressNextTapRef = useRef(false);

  const applyTokens = useCallback((next: TokenPair | null) => {
    tokenRef.current = next;
    setTokensState(next);
    saveTokens(next);
  }, []);

  const navigate = useCallback((nextRoute: AppRoute, replace = false) => {
    const normalized = normalizeRoute(nextRoute);
    setRoute((current) => {
      if (current === normalized && !replace) {
        return current;
      }

      if (replace) {
        window.history.replaceState(null, "", normalized);
      } else {
        window.history.pushState(null, "", normalized);
      }

      return normalized;
    });
  }, []);

  const resetChatState = useCallback(() => {
    for (const timerId of Object.values(presenceOfflineTimersRef.current)) {
      window.clearTimeout(timerId);
    }
    presenceOfflineTimersRef.current = {};
    if (typeof window !== "undefined") {
      window.sessionStorage.removeItem(PRESENCE_CACHE_KEY);
    }

    setKnownChats([]);
    setUnreadByChatId({});
    setMessagesByChat({});
    paginationRef.current = {};
    readSyncedServerIdsByChatRef.current = {};
    readSyncInFlightRef.current.clear();
    setPaginationByChat({});
    setActiveChatId(null);
    setActiveDraftPeerUserId(null);
    setComposerValue("");
    setMessageEditDraft(null);
    setPresenceByUserId({});
    setPresencePendingOfflineByUserId({});
    setDeleteMessageBusyById({});
    setNewChatTagInput("");
    setChatSearchResults([]);
    setChatSearchBusy(false);
    setChatSearchError(null);
    setMessageMenu(null);
    setChatMenu(null);
    setReactionsByMessageId({});
    setMyReactionsByMessageId({});
    setChatTypeById({});
    setGroupInfoOpen(false);
    setActiveChatParticipants([]);
    setReplyToMessage(null);
    setPendingFiles([]);
    setChatRenameOpen(false);
    wsMatchedLocalMessageIdsRef.current.clear();
    unreadHydrationRequestIdRef.current += 1;
  }, []);

  const handleAuthLost = useCallback(() => {
    setProfile(null);
    setProfileError(null);
    setProfileViewUserId(null);
    setProfileViewData(null);
    setProfileViewError(null);
    resetChatState();
    navigate("/login", true);
    setUserNamesById({});
    userNamesRef.current = {};
    loadingUserIdsRef.current.clear();
    setWsStatus("offline");
    setPresenceByUserId({});
    setNotice("Сессия истекла. Войдите снова.");
  }, [navigate, resetChatState]);

  const api = useMemo(
    () => createApiClient({
      getTokens: () => tokenRef.current,
      setTokens: applyTokens,
      onAuthLost: handleAuthLost
    }),
    [applyTokens, handleAuthLost]
  );

  const upsertUserAvatar = useCallback((userId: number, avatarUrl: string | null | undefined) => {
    setAvatarUrlByUserId((previous) => {
      if (previous[userId] === (avatarUrl ?? null)) return previous;
      return { ...previous, [userId]: avatarUrl ?? null };
    });
  }, []);

  const upsertUserName = useCallback((userId: number, name: string) => {
    const normalized = name.trim();
    if (!normalized) {
      return;
    }

    setUserNamesById((previous) => {
      if (previous[userId] === normalized) {
        return previous;
      }

      const next = {
        ...previous,
        [userId]: normalized
      };
      userNamesRef.current = next;
      return next;
    });
  }, []);

  const ensureUserNamesLoaded = useCallback((userIds: Array<number | null | undefined>) => {
    const currentProfile = profileRef.current;
    const unique = new Set<number>();

    for (const candidate of userIds) {
      if (typeof candidate === "number" && Number.isInteger(candidate) && candidate > 0) {
        unique.add(candidate);
      }
    }

    for (const userId of unique) {
      if (currentProfile && userId === currentProfile.userId) {
        upsertUserName(userId, currentProfile.name);
        upsertUserAvatar(userId, currentProfile.avatarUrl);
        continue;
      }

      if (userNamesRef.current[userId] || loadingUserIdsRef.current.has(userId)) {
        continue;
      }

      loadingUserIdsRef.current.add(userId);

      void (async () => {
        try {
          const user = await api.getUserById(userId);
          upsertUserName(user.userId, user.name);
          upsertUserAvatar(user.userId, user.avatarUrl);
        } catch {
          // Ignore missing users and transient API errors, UI falls back to "Неизвестный пользователь".
        } finally {
          loadingUserIdsRef.current.delete(userId);
        }
      })();
    }
  }, [api, upsertUserAvatar, upsertUserName]);

  const getUserDisplayName = useCallback((userId: number | null | undefined): string => {
    if (typeof userId !== "number") {
      return "Неизвестный пользователь";
    }

    if (profile && userId === profile.userId) {
      return profile.name;
    }

    return userNamesById[userId] ?? "Неизвестный пользователь";
  }, [profile, userNamesById]);

  const setPaginationForChat = useCallback((chatId: number, updater: (prev: ChatPagination) => ChatPagination) => {
    setPaginationByChat((previous) => {
      const key = String(chatId);
      const next = updater(getChatPagination(previous, key));
      const snapshot = {
        ...previous,
        [key]: next
      };
      paginationRef.current = snapshot;
      return snapshot;
    });
  }, []);

  const scrollToBottom = useCallback((behavior: ScrollBehavior = "auto") => {
    const timeline = timelineRef.current;
    if (!timeline) {
      return;
    }

    timeline.scrollTo({
      top: timeline.scrollHeight,
      behavior
    });
  }, []);

  const upsertChat = useCallback((
    chatId: number,
    peerUserId: number | null,
    updatedAt?: string,
    chatName?: string,
    preview?: { text: string | null; userId: number | null; hasMedia: boolean | null } | null
  ) => {
    setKnownChats((previous) => {
      const existing = previous.find((chat) => chat.chatId === chatId);

      if (!existing) {
        return sortChats([
          ...previous,
          {
            chatId,
            chatName: chatName ?? "",
            peerUserId,
            updatedAt: updatedAt ?? formatLocalDateTime(Date.now()),
            lastMessagePreview: preview?.text ?? null,
            lastMessageUserId: preview?.userId ?? null,
            lastMessageHasMedia: preview?.hasMedia ?? null
          }
        ]);
      }

      const nextUpdatedAt = chooseLatestIso(existing.updatedAt, updatedAt);
      const orderChanged = nextUpdatedAt !== existing.updatedAt;

      const mapped = previous.map((chat) => {
        if (chat.chatId !== chatId) {
          return chat;
        }
        return {
          ...chat,
          peerUserId: peerUserId ?? chat.peerUserId,
          chatName: chatName ?? chat.chatName,
          updatedAt: nextUpdatedAt,
          ...(preview !== undefined ? {
            lastMessagePreview: preview?.text ?? null,
            lastMessageUserId: preview?.userId ?? null,
            lastMessageHasMedia: preview?.hasMedia ?? null
          } : {})
        };
      });

      return orderChanged ? sortChats(mapped) : mapped;
    });
  }, []);

  const removeChatFromState = useCallback((chatId: number) => {
    const key = String(chatId);

    setKnownChats((previous) => previous.filter((chat) => chat.chatId !== chatId));
    setMessagesByChat((previous) => {
      if (!(key in previous)) {
        return previous;
      }

      const next = { ...previous };
      delete next[key];
      return next;
    });
    setPaginationByChat((previous) => {
      if (!(key in previous)) {
        return previous;
      }

      const next = { ...previous };
      delete next[key];
      paginationRef.current = next;
      return next;
    });
    setUnreadByChatId((previous) => {
      if (!previous[chatId]) {
        return previous;
      }

      const next = { ...previous };
      delete next[chatId];
      return next;
    });
    setActiveChatId((previous) => (previous === chatId ? null : previous));
  }, []);

  const appendMessage = useCallback((nextMessage: ChatMessage) => {
    setMessagesByChat((previous) => {
      const key = String(nextMessage.chatId);
      const current = previous[key] ?? [];

      return {
        ...previous,
        [key]: mergeChatMessages(current, [nextMessage])
      };
    });
  }, []);

  const updateMessageDelivery = useCallback((chatId: number, messageId: string, delivery: ChatMessage["delivery"]) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key] ?? [];

      return {
        ...previous,
        [key]: current.map((message) =>
          message.id === messageId
            ? {
                ...message,
                delivery
              }
            : message
        )
      };
    });
  }, []);

  const updateMessagePhotoLinks = useCallback((chatId: number, messageId: string, photoLinks: string[]) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key] ?? [];
      return {
        ...previous,
        [key]: current.map((message) =>
          message.id === messageId ? { ...message, photoLinks } : message
        )
      };
    });
  }, []);

  const removeMessageFromState = useCallback((chatId: number, messageId: string) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key];
      if (!current) {
        return previous;
      }

      const nextMessages = current.filter((message) => message.id !== messageId);
      if (nextMessages.length === current.length) {
        return previous;
      }

      return {
        ...previous,
        [key]: nextMessages
      };
    });
  }, []);

  const removeMessageByServerId = useCallback((chatId: number, serverId: string) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key];
      if (!current) {
        return previous;
      }

      const nextMessages = current.filter((message) => normalizeServerId(message.serverId) !== serverId);
      if (nextMessages.length === current.length) {
        return previous;
      }

      return {
        ...previous,
        [key]: nextMessages
      };
    });
  }, []);

  const updateMessageServerId = useCallback((chatId: number, messageId: string, serverId: string) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key] ?? [];
      let changed = false;

      const nextMessages = current.map((message) => {
        if (message.id !== messageId) {
          return message;
        }

        if (normalizeServerId(message.serverId) === serverId) {
          return message;
        }

        changed = true;
        return {
          ...message,
          serverId
        };
      });

      if (!changed) {
        return previous;
      }

      return {
        ...previous,
        [key]: nextMessages
      };
    });
  }, []);

  const updateMessageContentById = useCallback((
    chatId: number,
    messageId: string,
    content: string,
    edited = true
  ) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key] ?? [];
      let changed = false;

      const nextMessages = current.map((message) => {
        if (message.id !== messageId) {
          return message;
        }

        if (message.content === content && message.edited === edited) {
          return message;
        }

        changed = true;
        return {
          ...message,
          content,
          edited
        };
      });

      if (!changed) {
        return previous;
      }

      return {
        ...previous,
        [key]: nextMessages
      };
    });
  }, []);

  const updateMessageContentByServerId = useCallback((
    chatId: number,
    serverId: string,
    content: string,
    edited = true
  ) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key] ?? [];
      let changed = false;

      const nextMessages = current.map((message) => {
        if (normalizeServerId(message.serverId) !== serverId) {
          return message;
        }

        if (message.content === content && message.edited === edited) {
          return message;
        }

        changed = true;
        return {
          ...message,
          content,
          edited
        };
      });

      if (!changed) {
        return previous;
      }

      return {
        ...previous,
        [key]: nextMessages
      };
    });
  }, []);

  const markMessageAsSent = useCallback((
    chatId: number,
    messageId: string,
    serverId: string | null,
    serverCreatedAt: string | null
  ) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key] ?? [];
      let changed = false;

      const nextMessages = current.map((message) => {
        if (message.id !== messageId) {
          return message;
        }

        const nextServerId = serverId ?? normalizeServerId(message.serverId);
        const nextCreatedAt = serverCreatedAt ?? message.createdAt;
        const nextDelivery: ChatMessage["delivery"] = message.delivery === "read" ? "read" : "sent";

        const sameServerId = normalizeServerId(message.serverId) === nextServerId;
        const sameCreatedAt = message.createdAt === nextCreatedAt;
        const sameDelivery = message.delivery === nextDelivery;
        if (sameServerId && sameCreatedAt && sameDelivery) {
          return message;
        }

        changed = true;
        return {
          ...message,
          serverId: nextServerId ?? undefined,
          createdAt: nextCreatedAt,
          delivery: nextDelivery
        };
      });

      if (!changed) {
        return previous;
      }

      return {
        ...previous,
        [key]: nextMessages
      };
    });
  }, []);

  const markMessageAsReadByServerId = useCallback((chatId: number, serverId: string) => {
    setMessagesByChat((previous) => {
      const key = String(chatId);
      const current = previous[key] ?? [];
      let changed = false;

      const nextMessages: ChatMessage[] = current.map((message): ChatMessage => {
        if (normalizeServerId(message.serverId) !== serverId) {
          return message;
        }

        if (message.delivery === "read") {
          return message;
        }

        changed = true;
        return {
          ...message,
          delivery: "read"
        };
      });

      if (!changed) {
        return previous;
      }

      return {
        ...previous,
        [key]: nextMessages
      };
    });
  }, []);

  const resolveServerIdForMessage = useCallback(async (message: ChatMessage): Promise<string | null> => {
    const messageTimestamp = parseMessageTime(message.createdAt);
    let bestCandidateId: string | null = null;
    let bestCandidateDistance = Number.POSITIVE_INFINITY;

    for (let page = 0; page < MESSAGE_LOOKUP_MAX_PAGES; page += 1) {
      const slice = await api.getMessages(message.chatId, MESSAGE_LOOKUP_PAGE_SIZE, page);

      for (const historyItem of slice.content) {
        if (historyItem.userId !== message.userId || historyItem.content !== message.content) {
          continue;
        }

        const candidateId = normalizeServerId(historyItem.id ?? undefined);
        if (!candidateId) {
          continue;
        }

        if (messageTimestamp === null) {
          return candidateId;
        }

        const candidateTimestamp = parseMessageTime(historyItem.sendAt ?? historyItem.createdAt ?? null);
        if (candidateTimestamp === null) {
          if (bestCandidateId === null) {
            bestCandidateId = candidateId;
          }
          continue;
        }

        const candidateDistance = Math.abs(candidateTimestamp - messageTimestamp);
        if (candidateDistance < bestCandidateDistance) {
          bestCandidateDistance = candidateDistance;
          bestCandidateId = candidateId;
        }
      }

      if (slice.last || slice.content.length === 0) {
        break;
      }
    }
    return bestCandidateId;
  }, [api]);

  const syncReadReceiptsForActiveChat = useCallback(async () => {
    if (!profile || activeChatId === null) {
      return;
    }

    const chatKey = String(activeChatId);
    const chatMessages = messagesByChat[chatKey] ?? [];
    const syncedByChat = readSyncedServerIdsByChatRef.current;
    if (!syncedByChat[chatKey]) {
      syncedByChat[chatKey] = new Set<string>();
    }

    const alreadySynced = syncedByChat[chatKey];
    const idsToSync: string[] = [];

    for (const message of chatMessages) {
      if (message.userId === profile.userId) {
        continue;
      }

      const serverId = normalizeServerId(message.serverId);
      if (!serverId) {
        continue;
      }

      const inFlightKey = `${chatKey}:${serverId}`;
      if (alreadySynced.has(serverId) || readSyncInFlightRef.current.has(inFlightKey)) {
        continue;
      }

      readSyncInFlightRef.current.add(inFlightKey);
      idsToSync.push(serverId);

      if (idsToSync.length >= READ_SYNC_BATCH_SIZE) {
        break;
      }
    }

    if (idsToSync.length === 0) {
      return;
    }

    try {
      await api.readMessages(idsToSync);
      for (const id of idsToSync) {
        alreadySynced.add(id);
      }
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      for (const id of idsToSync) {
        readSyncInFlightRef.current.delete(`${chatKey}:${id}`);
      }
    }
  }, [activeChatId, api, messagesByChat, profile]);

  const loadHistoryPage = useCallback(async (chatId: number, mode: "initial" | "prepend") => {
    if (!profile) {
      return;
    }

    const key = String(chatId);
    const pagination = getChatPagination(paginationRef.current, key);

    if (pagination.loading) {
      return;
    }

    if (mode === "prepend" && (!pagination.initialized || !pagination.hasMore)) {
      return;
    }

    const page = mode === "initial" ? 0 : pagination.nextOffset;
    const timeline = timelineRef.current;
    const previousHeight = timeline?.scrollHeight ?? 0;
    const previousTop = timeline?.scrollTop ?? 0;

    setPaginationForChat(chatId, (current) => ({
      ...current,
      loading: true,
      ...(mode === "initial"
        ? {
            initialized: false,
            nextOffset: 0,
            hasMore: true
          }
        : {})
    }));

    try {
      const slice = await api.getMessages(chatId, HISTORY_PAGE_SIZE, page);
      const incoming = slice.content.map((message) => toHistoryChatMessage(message, profile.userId));
      ensureUserNamesLoaded(incoming.map((message) => message.userId));

      setMessagesByChat((previous) => {
        const current = previous[key] ?? [];
        const sanitizedCurrent = pruneLocalEchoes(current, incoming);
        return {
          ...previous,
          [key]: mergeChatMessages(sanitizedCurrent, incoming)
        };
      });

      const serverIds = incoming
        .map((m) => (typeof m.serverId === "string" ? m.serverId : null))
        .filter((id): id is string => id !== null);
      if (serverIds.length > 0) {
        void api.batchGetReactions(profile.userId, serverIds).then(({ reactions }) => {
          setReactionsByMessageId((prev) => ({ ...prev, ...reactions }));
          setMyReactionsByMessageId((prev) => {
            const updates: Record<string, string[]> = {};
            for (const [msgId, msgReactions] of Object.entries(reactions)) {
              updates[msgId] = msgReactions
                .filter((r) => r.reactedByCurrentUser)
                .map((r) => r.reactionType);
            }
            return { ...prev, ...updates };
          });
        }).catch(() => undefined);
      }

      setPaginationForChat(chatId, (current) => ({
        ...current,
        loading: false,
        initialized: true,
        nextOffset: page + 1,
        hasMore: !slice.last
      }));

      if (mode === "initial") {
        requestAnimationFrame(() => {
          const saved = scrollPositionByChat.current[chatId];
          if (saved !== undefined && timelineRef.current) {
            timelineRef.current.scrollTop = saved;
            stickToBottomRef.current = false;
          } else {
            stickToBottomRef.current = true;
            scrollToBottom();
          }
        });
        return;
      }

      requestAnimationFrame(() => {
        const container = timelineRef.current;
        if (!container) {
          return;
        }

        const newHeight = container.scrollHeight;
        container.scrollTop = Math.max(0, newHeight - previousHeight + previousTop);
      });
    } catch (error) {
      setPaginationForChat(chatId, (current) => ({
        ...current,
        loading: false,
        initialized: true
      }));
      setNotice(toMessageText(error));
    }
  }, [api, ensureUserNamesLoaded, profile, scrollToBottom, setPaginationForChat, setReactionsByMessageId]);

  const submitMessage = useCallback(async (
    chatId: number,
    content: string,
    messageId: string,
    repliedMessageId?: string | null,
    photoLinks?: string[] | null
  ) => {
    try {
      const saved = await api.sendMessage(chatId, content, repliedMessageId, photoLinks);
      const savedServerId = normalizeServerId(saved.id ?? undefined);
      const savedCreatedAt = saved.sendAt ?? saved.createdAt ?? null;
      markMessageAsSent(chatId, messageId, savedServerId, savedCreatedAt);
    } catch (error) {
      updateMessageDelivery(chatId, messageId, "failed");
      setNotice(toMessageText(error));
    }
  }, [api, markMessageAsSent, updateMessageDelivery]);

  const loadUnreadCountForChat = useCallback(async (chatId: number, myUserId: number): Promise<number> => {
    let unread = 0;

    for (let page = 0; page < UNREAD_SCAN_MAX_PAGES; page += 1) {
      const slice = await api.getMessages(chatId, UNREAD_SCAN_PAGE_SIZE, page);

      for (const message of slice.content) {
        if (message.userId !== myUserId && message.readStatus === false) {
          unread += 1;
        }
      }

      if (slice.last || slice.content.length === 0) {
        break;
      }
    }

    return unread;
  }, [api]);

  const hydrateUnreadCounts = useCallback(async (chats: KnownChat[], myUserId: number) => {
    const requestId = unreadHydrationRequestIdRef.current + 1;
    unreadHydrationRequestIdRef.current = requestId;

    if (chats.length === 0) {
      if (unreadHydrationRequestIdRef.current === requestId) {
        setUnreadByChatId({});
      }
      return;
    }

    const counts: Record<number, number> = {};
    const chatIds = chats.map((chat) => chat.chatId);
    let cursor = 0;
    const workerCount = Math.min(UNREAD_SCAN_CONCURRENCY, chatIds.length);

    await Promise.all(
      Array.from({ length: workerCount }, async () => {
        while (true) {
          const index = cursor;
          cursor += 1;
          if (index >= chatIds.length) {
            break;
          }

          const chatId = chatIds[index];
          try {
            const unread = await loadUnreadCountForChat(chatId, myUserId);
            if (unread > 0) {
              counts[chatId] = unread;
            }
          } catch {
            // Ignore per-chat errors to avoid breaking unread hydration.
          }
        }
      })
    );

    if (unreadHydrationRequestIdRef.current !== requestId) {
      return;
    }

    const nextCounts = { ...counts };
    // Use refs so this callback doesn't need activeChatId/route as deps —
    // those deps would recreate hydrateUnreadCounts → syncChatsFromServer on
    // every chat switch, causing a full setKnownChats reset and subtitle flicker.
    if (routeRef.current === "/" && activeChatIdRef.current !== null) {
      delete nextCounts[activeChatIdRef.current];
    }

    setUnreadByChatId(nextCounts);
  }, [loadUnreadCountForChat]);

  const syncChatsFromServer = useCallback(async (myUserId: number): Promise<KnownChat[]> => {
    const collected: ChatInfo[] = [];

    for (let offset = 0; offset < CHAT_SYNC_MAX_PAGES; offset += 1) {
      const slice = await api.getChats(CHAT_SYNC_PAGE_SIZE, offset);
      collected.push(...slice.content);
      if (slice.last || slice.content.length === 0) {
        break;
      }
    }

    const nextChats = mapChatsFromServer(collected);
    setKnownChats(nextChats);
    void hydrateUnreadCounts(nextChats, myUserId);

    // Backfill last-message preview for chats where the backend has no stored preview yet.
    // Runs concurrently (4 at a time) for the top 30 most-recent chats.
    void (async () => {
      const needPreview = nextChats
        .filter((c) => c.updatedAt !== "1970-01-01T00:00:00.000" && !c.lastMessagePreview && !c.lastMessageHasMedia)
        .slice(0, 30);
      const CONCURRENCY = 4;
      for (let i = 0; i < needPreview.length; i += CONCURRENCY) {
        await Promise.all(
          needPreview.slice(i, i + CONCURRENCY).map(async (chat) => {
            try {
              const slice = await api.getMessages(chat.chatId, 1, 0);
              const msg = slice.content[0];
              if (!msg) return;
              const hasMedia = Array.isArray(msg.photoLinks) && msg.photoLinks.length > 0;
              setKnownChats((prev) => prev.map((c) =>
                c.chatId === chat.chatId
                  ? {
                      ...c,
                      lastMessagePreview: msg.content ?? null,
                      lastMessageUserId: typeof msg.userId === "number" ? msg.userId : null,
                      lastMessageHasMedia: hasMedia
                    }
                  : c
              ));
              // Also seed messagesByChat so WS / open-chat code can merge correctly
              const mapped = toHistoryChatMessage(msg, myUserId);
              setMessagesByChat((prev) => {
                const key = String(chat.chatId);
                const existing = prev[key];
                if (existing && existing.length > 0) return prev;
                return { ...prev, [key]: [mapped] };
              });
            } catch {
              // ignore per-chat errors
            }
          })
        );
      }
    })();

    // Resolve peer user IDs and chat types asynchronously for each chat
    void Promise.all(
      nextChats.map(async (chat) => {
        try {
          const participants = await api.getChatUsers(chat.chatId);
          const others = participants.filter((p) => p.userId !== myUserId);
          const isPrivate = others.length === 1;
          setChatTypeById((prev) => ({
            ...prev,
            [chat.chatId]: isPrivate ? "PRIVATE" : "GROUP"
          }));
          if (isPrivate && others[0]) {
            upsertChat(chat.chatId, others[0].userId);
            ensureUserNamesLoaded([others[0].userId]);
          } else {
            ensureUserNamesLoaded(others.map((p) => p.userId));
          }
        } catch {
          // Ignore per-chat participant load failures
        }
      })
    );

    setUnreadByChatId((previous) => {
      const allowedChatIds = new Set(nextChats.map((chat) => chat.chatId));
      let changed = false;
      const next: Record<number, number> = {};

      for (const [chatIdText, count] of Object.entries(previous)) {
        const chatId = Number(chatIdText);
        if (Number.isInteger(chatId) && allowedChatIds.has(chatId) && count > 0) {
          next[chatId] = count;
          continue;
        }

        changed = true;
      }

      return changed ? next : previous;
    });
    return nextChats;
  }, [api, ensureUserNamesLoaded, hydrateUnreadCounts, upsertChat]);

  const onSocketMessage = useCallback((event: SocketMessageEvent) => {
    const eventTimestamp = Date.now();
    const incomingServerId = normalizeServerId(event.id);
    const key = String(event.chatId);
    const currentActiveChatId = activeChatIdRef.current;
    const currentRoute = routeRef.current;
    const currentProfile = profileRef.current;
    const timeline = timelineRef.current;
    const shouldAutoScroll = currentActiveChatId === event.chatId
      && (timeline ? isNearBottom(timeline) : stickToBottomRef.current);

    setMessagesByChat((previous) => {
      const current = previous[key] ?? [];

      if (incomingServerId !== null && current.some((message) => normalizeServerId(message.serverId) === incomingServerId)) {
        return previous;
      }

      let localEchoIndex = -1;
      let bestLocalEchoDistance = Number.POSITIVE_INFINITY;

      for (let index = 0; index < current.length; index += 1) {
        const candidate = current[index];
        if (candidate.origin !== "local" || hasServerId(candidate)) {
          continue;
        }

        if (candidate.userId !== event.userId || candidate.content !== event.content || candidate.delivery === "failed") {
          continue;
        }

        if (wsMatchedLocalMessageIdsRef.current.has(candidate.id)) {
          continue;
        }

        const candidateTimestamp = parseMessageTime(candidate.createdAt);
        if (candidateTimestamp === null) {
          continue;
        }

        const distance = Math.abs(eventTimestamp - candidateTimestamp);
        if (distance > LOCAL_ECHO_MATCH_WINDOW_MS || distance >= bestLocalEchoDistance) {
          continue;
        }

        localEchoIndex = index;
        bestLocalEchoDistance = distance;
      }

      if (localEchoIndex >= 0) {
        const next = [...current];
        const matchedLocal = next[localEchoIndex];
        wsMatchedLocalMessageIdsRef.current.add(matchedLocal.id);
        next[localEchoIndex] = {
          ...matchedLocal,
          serverId: incomingServerId ?? matchedLocal.serverId,
          edited: typeof event.editStatus === "boolean" ? event.editStatus : matchedLocal.edited,
          delivery: matchedLocal.delivery === "read" ? "read" : "sent"
        };

        return {
          ...previous,
          [key]: next
        };
      }

      const hasRecentRemoteDuplicate = current.some((candidate) => {
        if (candidate.origin !== "remote" || hasServerId(candidate)) {
          return false;
        }

        if (candidate.userId !== event.userId || candidate.content !== event.content) {
          return false;
        }

        const candidateTimestamp = parseMessageTime(candidate.createdAt);
        return candidateTimestamp !== null && Math.abs(eventTimestamp - candidateTimestamp) <= WS_REMOTE_DUPLICATE_WINDOW_MS;
      });

      if (hasRecentRemoteDuplicate) {
        return previous;
      }

      return {
        ...previous,
        [key]: mergeChatMessages(current, [{
          id: incomingServerId !== null ? `server-${incomingServerId}` : makeLocalMessageId(),
          chatId: event.chatId,
          userId: event.userId,
          content: event.content ?? "",
          createdAt: event.sendAt ?? formatLocalDateTime(eventTimestamp),
          serverId: incomingServerId ?? undefined,
          edited: Boolean(event.editStatus),
          delivery: "sent",
          origin: currentProfile && event.userId === currentProfile.userId ? "local" : "remote",
          repliedMessage: null,
          photoLinks: Array.isArray(event.photoLinks) ? event.photoLinks : null
        }])
      };
    });

    ensureUserNamesLoaded([event.userId]);

    // Clear typing indicator for the sender immediately on message received
    const typingKey = `${event.chatId}:${event.userId}`;
    if (typingTimeoutsRef.current[typingKey]) {
      window.clearTimeout(typingTimeoutsRef.current[typingKey]);
      delete typingTimeoutsRef.current[typingKey];
    }
    setTypingByChatId((prev) => {
      const chat = prev[event.chatId];
      if (!chat || !(event.userId in chat)) return prev;
      const next = { ...chat };
      delete next[event.userId];
      return { ...prev, [event.chatId]: next };
    });

    const wsHasMedia = Array.isArray(event.photoLinks) && event.photoLinks.length > 0;
    upsertChat(
      event.chatId,
      currentProfile && event.userId !== currentProfile.userId ? event.userId : null,
      formatLocalDateTime(eventTimestamp),
      undefined,
      { text: event.content ?? null, userId: event.userId, hasMedia: wsHasMedia }
    );

    const myUserId = currentProfile?.userId ?? null;
    const isIncomingForMe = myUserId !== null && event.userId !== myUserId;
    const isCurrentlyOpenChat = currentRoute === "/" && currentActiveChatId === event.chatId;
    if (isIncomingForMe && !isCurrentlyOpenChat) {
      setUnreadByChatId((previous) => ({
        ...previous,
        [event.chatId]: (previous[event.chatId] ?? 0) + 1
      }));
    }

    if (shouldAutoScroll) {
      requestAnimationFrame(() => {
        stickToBottomRef.current = true;
        scrollToBottom("smooth");
      });
    }
  }, [ensureUserNamesLoaded, scrollToBottom, upsertChat]);

  const onSocketMessageRead = useCallback((event: SocketMessageReadEvent) => {
    if (!event.readStatus) {
      return;
    }

    const serverId = normalizeServerId(event.id);
    if (!serverId) {
      return;
    }

    markMessageAsReadByServerId(event.chatId, serverId);
  }, [markMessageAsReadByServerId]);

  const onSocketMessageDelete = useCallback((event: SocketMessageDeleteEvent) => {
    const serverId = normalizeServerId(event.id);
    if (!serverId) {
      return;
    }

    removeMessageByServerId(event.chatId, serverId);
  }, [removeMessageByServerId]);

  const onSocketMessageEdit = useCallback((event: SocketMessageEditEvent) => {
    const serverId = normalizeServerId(event.id);
    if (!serverId) {
      return;
    }

    updateMessageContentByServerId(event.chatId, serverId, event.content, Boolean(event.editStatus));
  }, [updateMessageContentByServerId]);

  const onSocketPresence = useCallback((event: SocketPresenceEvent) => {
    const timers = presenceOfflineTimersRef.current;
    const clearOfflineTimer = () => {
      const timerId = timers[event.userId];
      if (typeof timerId === "number") {
        window.clearTimeout(timerId);
        delete timers[event.userId];
      }
    };

    if (event.online) {
      clearOfflineTimer();
      setPresencePendingOfflineByUserId((previous) => {
        if (!previous[event.userId]) {
          return previous;
        }

        const next = { ...previous };
        delete next[event.userId];
        return next;
      });
      setPresenceByUserId((previous) => {
        if (previous[event.userId] === true) {
          return previous;
        }

        return {
          ...previous,
          [event.userId]: true
        };
      });
      return;
    }

    clearOfflineTimer();
    const wasOnline = Boolean(presenceByUserIdRef.current[event.userId]);
    if (!wasOnline) {
      setPresencePendingOfflineByUserId((previous) => {
        if (!previous[event.userId]) {
          return previous;
        }

        const next = { ...previous };
        delete next[event.userId];
        return next;
      });
      setPresenceByUserId((previous) => {
        if (previous[event.userId] === false) {
          return previous;
        }

        return {
          ...previous,
          [event.userId]: false
        };
      });
      return;
    }

    setPresencePendingOfflineByUserId((previous) => ({
      ...previous,
      [event.userId]: true
    }));
    timers[event.userId] = window.setTimeout(() => {
      delete timers[event.userId];
      setPresencePendingOfflineByUserId((previous) => {
        if (!previous[event.userId]) {
          return previous;
        }

        const next = { ...previous };
        delete next[event.userId];
        return next;
      });
      setPresenceByUserId((previous) => {
        if (previous[event.userId] === false) {
          return previous;
        }

        return {
          ...previous,
          [event.userId]: false
        };
      });
    }, PRESENCE_OFFLINE_DELAY_MS);
  }, []);

  const onSocketReaction = useCallback((event: SocketReactionEvent) => {
    if (event.userId === profileRef.current?.userId) {
      return;
    }

    setReactionsByMessageId((previous) => {
      const current = previous[event.messageId] ?? [];
      const existing = current.find((r) => r.reactionType === event.reactionType);

      let next: Reaction[];
      if (event.type === "reaction_added") {
        if (existing) {
          next = current.map((r) =>
            r.reactionType === event.reactionType ? { ...r, count: r.count + 1 } : r
          );
        } else {
          next = [...current, { reactionType: event.reactionType, count: 1, reactedByCurrentUser: false }];
        }
      } else {
        if (!existing) return previous;
        if (existing.count <= 1) {
          next = current.filter((r) => r.reactionType !== event.reactionType);
        } else {
          next = current.map((r) =>
            r.reactionType === event.reactionType ? { ...r, count: r.count - 1 } : r
          );
        }
      }

      return { ...previous, [event.messageId]: next };
    });
  }, []);

  const onSocketTyping = useCallback((event: SocketTypingEvent) => {
    const myId = profileRef.current?.userId;
    if (event.userId === myId) return;
    const now = Date.now();
    setTypingByChatId((prev) => ({
      ...prev,
      [event.chatId]: { ...(prev[event.chatId] ?? {}), [event.userId]: now }
    }));
    const key = `${event.chatId}:${event.userId}`;
    if (typingTimeoutsRef.current[key]) window.clearTimeout(typingTimeoutsRef.current[key]);
    typingTimeoutsRef.current[key] = window.setTimeout(() => {
      setTypingByChatId((prev) => {
        const chat = prev[event.chatId];
        if (!chat) return prev;
        const next = { ...chat };
        delete next[event.userId];
        return { ...prev, [event.chatId]: next };
      });
      delete typingTimeoutsRef.current[key];
    }, TYPING_EXPIRE_MS);
  }, []);

  const handleTimelineScroll = useCallback(() => {
    const container = timelineRef.current;
    if (!container) return;

    const atBottom = isNearBottom(container);
    stickToBottomRef.current = atBottom;

    // Save scroll position (clear when at bottom)
    if (activeChatId !== null) {
      if (atBottom) {
        delete scrollPositionByChat.current[activeChatId];
      } else {
        scrollPositionByChat.current[activeChatId] = container.scrollTop;
      }
    }

    if (activeChatId === null || container.scrollTop > TOP_LOAD_THRESHOLD) {
      return;
    }

    void loadHistoryPage(activeChatId, "prepend");
  }, [activeChatId, loadHistoryPage]);

  useLayoutEffect(() => {
    const el = messageMenuRef.current;
    if (!el) return;
    const { width, height } = el.getBoundingClientRect();
    const pad = 8;
    const x = parseFloat(el.style.left);
    const y = parseFloat(el.style.top);
    const cx = Math.max(pad, Math.min(x, window.innerWidth - width - pad));
    const cy = Math.max(pad, Math.min(y, window.innerHeight - height - pad));
    if (cx !== x) el.style.left = `${cx}px`;
    if (cy !== y) el.style.top = `${cy}px`;
  }, [messageMenu]);

  useLayoutEffect(() => {
    const el = chatMenuRef.current;
    if (!el) return;
    const { width, height } = el.getBoundingClientRect();
    const pad = 8;
    const x = parseFloat(el.style.left);
    const y = parseFloat(el.style.top);
    const cx = Math.max(pad, Math.min(x, window.innerWidth - width - pad));
    const cy = Math.max(pad, Math.min(y, window.innerHeight - height - pad));
    if (cx !== x) el.style.left = `${cx}px`;
    if (cy !== y) el.style.top = `${cy}px`;
  }, [chatMenu]);

  useEffect(() => {
    document.documentElement.dataset.theme = themeMode;
    window.localStorage.setItem(THEME_STORAGE_KEY, themeMode);
  }, [themeMode]);

  useEffect(() => {
    const urls = pendingFiles.map((f) =>
      f.type.startsWith("image/") ? URL.createObjectURL(f) : null
    );
    setFilePreviewUrls(urls);
    return () => { urls.forEach((u) => u && URL.revokeObjectURL(u)); };
  }, [pendingFiles]);

  // Close emoji picker when clicking outside the composer area
  const composerFormRef = useRef<HTMLFormElement | null>(null);
  useEffect(() => {
    if (!emojiPickerOpen) return;
    const handle = (e: MouseEvent) => {
      if (composerFormRef.current && !composerFormRef.current.contains(e.target as Node)) {
        setEmojiPickerOpen(false);
      }
    };
    document.addEventListener("mousedown", handle);
    return () => document.removeEventListener("mousedown", handle);
  }, [emojiPickerOpen]);

  useEffect(() => {
    const previousRoute = previousRouteRef.current;
    previousRouteRef.current = route;

    const returnedToChat = route === "/" && (isSettingsRoute(previousRoute) || previousRoute === "/search");
    if (!returnedToChat || activeChatId === null) {
      return;
    }

    requestAnimationFrame(() => {
      stickToBottomRef.current = true;
      scrollToBottom();
    });
  }, [activeChatId, route, scrollToBottom]);

  useEffect(() => {
    const onPopState = () => {
      setRoute(normalizeRoute(window.location.pathname));
    };

    window.addEventListener("popstate", onPopState);
    return () => {
      window.removeEventListener("popstate", onPopState);
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || route !== "/" || messageMenu || chatMenu) {
        return;
      }

      if (activeChatId === null && activeDraftPeerUserId === null) {
        return;
      }

      setActiveChatId(null);
      setActiveDraftPeerUserId(null);
    };

    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [activeChatId, activeDraftPeerUserId, chatMenu, messageMenu, route]);

  useEffect(() => {
    if (!messageMenu && !chatMenu) {
      return;
    }

    const closeMenu = () => {
      setMessageMenu(null);
      setChatMenu(null);
    };

    const onPointerDown = (event: MouseEvent | TouchEvent) => {
      const target = event.target;
      if (target instanceof Element && target.closest(".tg-message-menu")) {
        return;
      }
      closeMenu();
    };

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        closeMenu();
      }
    };

    const onScroll = (event: Event) => {
      if (event.target instanceof Element && event.target.closest(".tg-message-menu")) {
        return;
      }
      closeMenu();
    };

    window.addEventListener("mousedown", onPointerDown);
    window.addEventListener("touchstart", onPointerDown);
    window.addEventListener("scroll", onScroll, true);
    window.addEventListener("resize", closeMenu);
    window.addEventListener("keydown", onKeyDown);

    return () => {
      window.removeEventListener("mousedown", onPointerDown);
      window.removeEventListener("touchstart", onPointerDown);
      window.removeEventListener("scroll", onScroll, true);
      window.removeEventListener("resize", closeMenu);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [chatMenu, messageMenu]);

  useEffect(() => {
    if (!tokensState && !isPublicRoute(route)) {
      navigate("/login", true);
    }
  }, [navigate, route, tokensState]);

  useEffect(() => {
    if (tokensState && profile && isPublicRoute(route)) {
      navigate("/", true);
    }
  }, [navigate, profile, route, tokensState]);

  useEffect(() => {
    if (!isSettingsRoute(route)) {
      return;
    }

    setActiveDraftPeerUserId(null);
    setActiveChatId(null);
  }, [route]);

  useEffect(() => {
    if (!tokensState) {
      setProfile(null);
      setProfileError(null);
      resetChatState();
      setProfileLoading(false);
      return;
    }

    const hasProfile = profileRef.current !== null;
    let cancelled = false;
    setProfileLoading(!hasProfile);
    setProfileError(null);
    if (!hasProfile) {
      setProfile(null);
    }

    void (async () => {
      try {
        const result = await api.getMyProfile();
        if (cancelled) {
          return;
        }

        setProfile(result);
        void syncChatsFromServer(result.userId).catch((chatLoadError) => {
          if (!cancelled) {
            setNotice(`Профиль загружен, но список чатов загрузить не удалось: ${toMessageText(chatLoadError)}`);
          }
        });
      } catch (error) {
        if (!cancelled) {
          const message = toMessageText(error);
          if (!hasProfile) {
            setProfile(null);
            setProfileError(message);
          }
          setNotice(hasProfile ? `Не удалось обновить сессию: ${message}` : message);
        }
      } finally {
        if (!cancelled) {
          setProfileLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [api, profileReloadTick, resetChatState, syncChatsFromServer, tokensState]);

  useEffect(() => {
    if (!notice) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      setNotice("");
    }, 5000);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [notice]);

  useEffect(() => {
    if (route !== "/search") {
      return;
    }

    requestAnimationFrame(() => {
      searchInputRef.current?.focus();
    });
  }, [route]);

  useEffect(() => {
    profileRef.current = profile;
  }, [profile]);

  useEffect(() => {
    presenceByUserIdRef.current = presenceByUserId;
  }, [presenceByUserId]);

  useEffect(() => {
    savePresenceCache(presenceByUserId);
  }, [presenceByUserId]);

  useEffect(() => {
    routeRef.current = route;
  }, [route]);

  useEffect(() => {
    activeChatIdRef.current = activeChatId;
  }, [activeChatId]);

  useEffect(() => {
    if (!profile) {
      return;
    }

    upsertUserName(profile.userId, profile.name);
    upsertUserAvatar(profile.userId, profile.avatarUrl);
    setProfileNameDraft(profile.name ?? "");
    setProfileTagDraft(profile.tag ?? "");
    setProfileDescriptionDraft(profile.description ?? "");
  }, [profile, upsertUserAvatar, upsertUserName]);

  useEffect(() => {
    const requestId = chatSearchRequestIdRef.current + 1;
    chatSearchRequestIdRef.current = requestId;

    if (!profile || route !== "/search") {
      setChatSearchBusy(false);
      setChatSearchError(null);
      return;
    }

    const normalizedTag = newChatTagInput.trim().replace(/^@+/, "");
    setChatSearchError(null);

    if (!normalizedTag || normalizedTag.length < 3) {
      setChatSearchResults([]);
      setChatSearchBusy(false);
      return;
    }

    setChatSearchBusy(true);
    const timeoutId = window.setTimeout(() => {
      void (async () => {
        try {
          const users = await api.searchUsersByTag(normalizedTag);
          if (chatSearchRequestIdRef.current !== requestId) {
            return;
          }

          const filteredUsers = users.filter((user) => user.userId !== profile.userId);
          setChatSearchResults(filteredUsers);
        } catch (error) {
          if (chatSearchRequestIdRef.current !== requestId) {
            return;
          }

          setChatSearchResults([]);
          setChatSearchError(toMessageText(error));
        } finally {
          if (chatSearchRequestIdRef.current === requestId) {
            setChatSearchBusy(false);
          }
        }
      })();
    }, USER_SEARCH_DEBOUNCE_MS);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [api, newChatTagInput, profile, route]);

  useEffect(() => {
    ensureUserNamesLoaded(knownChats.map((chat) => chat.peerUserId));
  }, [ensureUserNamesLoaded, knownChats]);

  // Group member search (create group modal)
  useEffect(() => {
    if (!createGroupOpen) {
      return;
    }
    const tag = groupSearchTag.trim().replace(/^@+/, "");
    if (!tag || tag.length < 3) {
      setGroupSearchResults([]);
      return;
    }
    setGroupSearchBusy(true);
    const id = window.setTimeout(async () => {
      try {
        const users = await api.searchUsersByTag(tag);
        const filtered = users.filter((u) => u.userId !== profile?.userId
          && !groupSelectedMembers.some((m) => m.userId === u.userId));
        setGroupSearchResults(filtered);
      } catch {
        setGroupSearchResults([]);
      } finally {
        setGroupSearchBusy(false);
      }
    }, USER_SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(id);
  }, [api, createGroupOpen, groupSearchTag, groupSelectedMembers, profile?.userId]);

  // Add member search
  useEffect(() => {
    if (!addMemberOpen) {
      return;
    }
    const tag = addMemberTag.trim().replace(/^@+/, "");
    if (!tag || tag.length < 3) {
      setAddMemberResults([]);
      return;
    }
    setAddMemberSearchBusy(true);
    const id = window.setTimeout(async () => {
      try {
        const users = await api.searchUsersByTag(tag);
        const filtered = users.filter((u) => u.userId !== profile?.userId
          && !activeChatParticipants.some((p) => p.userId === u.userId));
        setAddMemberResults(filtered);
      } catch {
        setAddMemberResults([]);
      } finally {
        setAddMemberSearchBusy(false);
      }
    }, USER_SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(id);
  }, [api, addMemberOpen, addMemberTag, activeChatParticipants, profile?.userId]);

  useEffect(() => {
    if (route !== "/profile" || !profile) {
      setProfileViewLoading(false);
      return;
    }

    const targetUserId = profileViewUserId ?? profile.userId;
    setProfileViewError(null);

    if (targetUserId === profile.userId) {
      setProfileViewData(profile);
      setProfileViewLoading(false);
      return;
    }

    let cancelled = false;
    setProfileViewLoading(true);

    void (async () => {
      try {
        const targetProfile = await api.getUserById(targetUserId);
        if (cancelled) {
          return;
        }
        setProfileViewData(targetProfile);
        upsertUserAvatar(targetProfile.userId, targetProfile.avatarUrl);
      } catch (error) {
        if (cancelled) {
          return;
        }
        setProfileViewData(null);
        setProfileViewError(toMessageText(error));
      } finally {
        if (!cancelled) {
          setProfileViewLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [api, profile, profileViewReloadTick, profileViewUserId, route, upsertUserAvatar]);

  const sortedChats = useMemo(
    () => sortChats(knownChats),
    [knownChats]
  );

  const presenceTargetUserIds = useMemo(
    () => {
      const ids = new Set<number>();

      for (const chat of sortedChats) {
        if (chat.peerUserId) {
          ids.add(chat.peerUserId);
        }
      }

      if (activeDraftPeerUserId) {
        ids.add(activeDraftPeerUserId);
      }

      // Track online status for group members when the panel is open
      if (groupInfoOpen) {
        for (const p of activeChatParticipants) {
          ids.add(p.userId);
        }
      }

      return Array.from(ids);
    },
    [activeDraftPeerUserId, activeChatParticipants, groupInfoOpen, sortedChats]
  );

  useEffect(() => {
    if (activeDraftPeerUserId !== null) {
      return;
    }

    if (activeChatId === null) {
      return;
    }

    const isKnownChat = sortedChats.some((chat) => chat.chatId === activeChatId);
    if (!isKnownChat) {
      setActiveChatId(null);
    }
  }, [activeChatId, activeDraftPeerUserId, sortedChats]);

  useEffect(() => {
    if (!tokensState || !profile) {
      realtimeRef.current?.disconnect();
      realtimeRef.current = null;
      setWsStatus("offline");
      return;
    }

    const bridge = new RealtimeBridge(
      async () => {
        const token = tokenRef.current?.token ?? null;
        if (!token) {
          return null;
        }

        await api.getMyUserId();
        return tokenRef.current?.token ?? null;
      },
      {
        onMessage: onSocketMessage,
        onMessageRead: onSocketMessageRead,
        onMessageDelete: onSocketMessageDelete,
        onMessageEdit: onSocketMessageEdit,
        onPresence: onSocketPresence,
        onReaction: onSocketReaction,
        onTyping: onSocketTyping,
        onStatus: setWsStatus,
        onError: (message) => setNotice(message)
      }
    );

    realtimeRef.current = bridge;
    bridge.connect();

    return () => {
      bridge.disconnect();
      if (realtimeRef.current === bridge) {
        realtimeRef.current = null;
      }
    };
  }, [
    api,
    onSocketMessage,
    onSocketMessageDelete,
    onSocketMessageEdit,
    onSocketMessageRead,
    onSocketPresence,
    onSocketReaction,
    onSocketTyping,
    profile?.userId,
    Boolean(tokensState)
  ]);

  useEffect(() => {
    realtimeRef.current?.setPresenceTargets(presenceTargetUserIds);
  }, [presenceTargetUserIds]);

  useEffect(() => {
    if (!profile || activeChatId === null) {
      return;
    }

    const pagination = getChatPagination(paginationRef.current, String(activeChatId));
    if (pagination.initialized || pagination.loading) {
      return;
    }

    void loadHistoryPage(activeChatId, "initial");
  }, [activeChatId, loadHistoryPage, profile]);

  useEffect(() => {
    if (!profile || activeChatId === null) {
      return;
    }

    const pagination = getChatPagination(paginationRef.current, String(activeChatId));
    if (!pagination.initialized || pagination.loading || !pagination.hasMore) {
      return;
    }

    const container = timelineRef.current;
    if (!container) {
      return;
    }

    const hasScrollableOverflow = container.scrollHeight > container.clientHeight + TOP_LOAD_THRESHOLD;
    if (hasScrollableOverflow) {
      return;
    }

    void loadHistoryPage(activeChatId, "prepend");
  }, [activeChatId, loadHistoryPage, paginationByChat, profile]);

  useEffect(() => {
    stickToBottomRef.current = true;

    if (activeChatId === null) {
      return;
    }

    requestAnimationFrame(() => {
      scrollToBottom();
    });
  }, [activeChatId, scrollToBottom]);

  useEffect(() => {
    if (route !== "/" || activeChatId === null || !profile) {
      return;
    }

    void syncReadReceiptsForActiveChat();
  }, [activeChatId, profile, route, syncReadReceiptsForActiveChat]);

  useEffect(() => {
    if (route !== "/" || activeChatId === null) {
      return;
    }

    setUnreadByChatId((previous) => {
      if (!previous[activeChatId]) {
        return previous;
      }

      const next = { ...previous };
      delete next[activeChatId];
      return next;
    });
  }, [activeChatId, route]);

  useEffect(() => {
    setMessageMenu(null);
    setChatMenu(null);
  }, [activeChatId, route]);

  useEffect(() => {
    if (activeChatId !== null && route === "/") {
      requestAnimationFrame(() => composerTextareaRef.current?.focus());
    }
  }, [activeChatId, route]);

  // Reset group state when switching chats to avoid stale data
  useEffect(() => {
    setActiveChatParticipants([]);
    setGroupInfoOpen(false);
    setChatRenameOpen(false);
  }, [activeChatId]);

  const currentMessages = useMemo(() => {
    if (activeChatId === null) {
      return [];
    }

    return messagesByChat[String(activeChatId)] ?? [];
  }, [activeChatId, messagesByChat]);

  useEffect(() => {
    if (!messageEditDraft) {
      return;
    }

    if (activeChatId !== messageEditDraft.chatId) {
      setMessageEditDraft(null);
      return;
    }

    const exists = currentMessages.some((message) => message.id === messageEditDraft.messageId);
    if (!exists) {
      setMessageEditDraft(null);
    }
  }, [activeChatId, currentMessages, messageEditDraft]);

  const editingMessage = useMemo(
    () => (messageEditDraft ? currentMessages.find((message) => message.id === messageEditDraft.messageId) ?? null : null),
    [currentMessages, messageEditDraft]
  );

  const isEditingMessage = Boolean(editingMessage);

  const messageMenuMessage = useMemo(
    () => (messageMenu ? currentMessages.find((message) => message.id === messageMenu.messageId) ?? null : null),
    [currentMessages, messageMenu]
  );
  const messageMenuMine = Boolean(
    messageMenuMessage && profile && (messageMenuMessage.userId === profile.userId || messageMenuMessage.origin === "local")
  );
  const messageMenuCanDelete = Boolean(
    messageMenuMessage
      && messageMenuMine
      && messageMenuMessage.delivery !== "pending"
      && messageMenuMessage.delivery !== "failed"
      && !deleteMessageBusyById[messageMenuMessage.id]
  );
  const messageMenuCanRetry = Boolean(messageMenuMessage && messageMenuMine && messageMenuMessage.delivery === "failed");
  const messageMenuCanEdit = Boolean(
    messageMenuMessage
      && messageMenuMine
      && messageMenuMessage.delivery !== "pending"
      && messageMenuMessage.delivery !== "failed"
  );
  const chatMenuChat = useMemo(
    () => (chatMenu ? sortedChats.find((chat) => chat.chatId === chatMenu.chatId) ?? null : null),
    [chatMenu, sortedChats]
  );

  const timelineRows = useMemo<TimelineRow[]>(() => {
    if (currentMessages.length === 0) {
      return [];
    }

    const rows: TimelineRow[] = [];
    let previousDayKey = "";

    for (const message of currentMessages) {
      const dayKey = dayKeyFromCreatedAt(message.createdAt);
      if (dayKey !== previousDayKey) {
        rows.push({
          kind: "day",
          key: `day-${dayKey}`,
          label: formatDayLabel(dayKey)
        });
        previousDayKey = dayKey;
      }

      rows.push({
        kind: "message",
        key: message.id,
        message
      });
    }

    return rows;
  }, [currentMessages]);

  const activeChat = useMemo(
    () => knownChats.find((chat) => chat.chatId === activeChatId) ?? null,
    [activeChatId, knownChats]
  );
  const activePeerUserId = activeChat?.peerUserId ?? activeDraftPeerUserId;
  const hasActiveConversation = activeChatId !== null || activeDraftPeerUserId !== null;

  const resolvePeerPresenceState = useCallback((userId: number | null | undefined) => {
    if (!userId) {
      return "offline" as const;
    }

    if (presenceByUserId[userId]) {
      return "online" as const;
    }

    if (presencePendingOfflineByUserId[userId]) {
      return "yellow" as const;
    }

    return "offline" as const;
  }, [presenceByUserId, presencePendingOfflineByUserId]);

  const resolvePeerPresenceLabel = useCallback((state: "online" | "yellow" | "offline") => {
    if (state === "online") {
      return "В сети";
    }

    if (state === "yellow") {
      return "Статус обновляется";
    }

    return "Не в сети";
  }, []);

  const activePeerPresenceState = useMemo(
    () => resolvePeerPresenceState(activePeerUserId),
    [activePeerUserId, resolvePeerPresenceState]
  );

  const activePagination = useMemo(() => {
    if (activeChatId === null) {
      return createDefaultPagination();
    }

    return getChatPagination(paginationByChat, String(activeChatId));
  }, [activeChatId, paginationByChat]);

  useEffect(() => {
    if (route !== "/" || activeChatId === null || !profile) {
      return;
    }

    if (!activePagination.initialized || activePagination.loading || currentMessages.length === 0) {
      return;
    }

    if (!stickToBottomRef.current) {
      return;
    }

    requestAnimationFrame(() => {
      scrollToBottom();
    });
  }, [
    activeChatId,
    activePagination.initialized,
    activePagination.loading,
    currentMessages.length,
    profile,
    route,
    scrollToBottom
  ]);

  const normalizedSearchTag = newChatTagInput.trim();
  const searchTooShort = normalizedSearchTag.length > 0 && normalizedSearchTag.length < 3;
  const showSearchIdleHint = normalizedSearchTag.length === 0;
  const showSearchNoResults = normalizedSearchTag.length >= 3
    && !chatSearchBusy
    && !chatSearchError
    && chatSearchResults.length === 0;

  const handleLogout = useCallback(() => {
    realtimeRef.current?.disconnect();
    realtimeRef.current = null;
    applyTokens(null);
    setProfile(null);
    setProfileError(null);
    setProfileViewUserId(null);
    setProfileViewData(null);
    setProfileViewError(null);
    resetChatState();
    navigate("/login", true);
    setUserNamesById({});
    userNamesRef.current = {};
    loadingUserIdsRef.current.clear();
    setWsStatus("offline");
  }, [applyTokens, navigate, resetChatState]);

  const handleRetryProfileLoad = useCallback(() => {
    setProfileReloadTick((previous) => previous + 1);
  }, []);

  const handleThemeToggle = useCallback(() => {
    setThemeMode((previous) => (previous === "light" ? "dark" : "light"));
  }, []);

  const handleLoginSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAuthBusy(true);

    try {
      const pair = await api.login({
        email: loginEmail.trim(),
        password: loginPassword
      });

      applyTokens(pair);
      setProfileError(null);
      setLoginPassword("");
      setNotice("Вход выполнен.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setAuthBusy(false);
    }
  };

  const handleRegisterSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedTag = registerTag.trim();
    if (normalizedTag.length < 3 || normalizedTag.length > 15) {
      setNotice("Тег должен быть от 3 до 15 символов.");
      return;
    }

    setAuthBusy(true);

    try {
      const pair = await api.register({
        email: registerEmail.trim(),
        password: registerPassword,
        name: registerName.trim(),
        tag: normalizedTag,
        description: registerDescription.trim() || null
      });

      applyTokens(pair);
      setProfileError(null);
      setRegisterPassword("");
      setRegisterTag("");
      setNotice("Аккаунт создан.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setAuthBusy(false);
    }
  };

  const handleProfileSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!profile) {
      return;
    }

    const normalizedName = profileNameDraft.trim();
    const normalizedTag = profileTagDraft.trim();
    const normalizedDescription = profileDescriptionDraft.trim();
    const payload: { name?: string; tag?: string; description?: string } = {};

    if (normalizedName && normalizedName !== profile.name) {
      if (normalizedName.length < 4) {
        setNotice("Имя должно быть не короче 4 символов.");
        return;
      }
      payload.name = normalizedName;
    }

    if (normalizedDescription !== (profile.description ?? "")) {
      payload.description = normalizedDescription;
    }

    if (normalizedTag && normalizedTag !== profile.tag) {
      if (normalizedTag.length < 3 || normalizedTag.length > 15) {
        setNotice("Тег должен быть от 3 до 15 символов.");
        return;
      }
      payload.tag = normalizedTag;
    }

    if (Object.keys(payload).length === 0) {
      setNotice("Нет изменений для сохранения.");
      return;
    }

    setProfileSaving(true);
    try {
      const updated = await api.editMyProfile(payload);
      setProfile(updated);
      setNotice("Профиль обновлён.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setProfileSaving(false);
    }
  };

  const handleProfileDelete = async () => {
    if (!profile) {
      return;
    }

    const confirmed = window.confirm(
      `Удалить аккаунт "${profile.name}"? Это действие необратимо и удалит ваши чаты.`
    );
    if (!confirmed) {
      return;
    }

    setProfileDeleteBusy(true);
    try {
      await api.deleteMyProfile();
      handleLogout();
      setNotice("Аккаунт удалён.");
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        handleLogout();
        setNotice("Аккаунт уже удалён.");
      } else {
        setNotice(toMessageText(error));
      }
    } finally {
      setProfileDeleteBusy(false);
    }
  };

  const handleCreateChatWithUser = async (peerUserId: number) => {
    if (!profile) {
      return;
    }

    if (peerUserId === profile.userId) {
      setNotice("Нельзя создать чат с самим собой.");
      return;
    }

    ensureUserNamesLoaded([peerUserId]);
    navigate("/");
    setActiveChatId(null);
    setActiveDraftPeerUserId(peerUserId);
    setComposerValue("");
    setMessageEditDraft(null);
    setNewChatTagInput("");
    setChatSearchResults([]);
    setChatSearchError(null);
  };

  const deleteChatById = useCallback(async (chatId: number, peerUserId: number | null, chatName?: string) => {
    const peerLabel = peerUserId ? getUserDisplayName(peerUserId) : (chatName ?? "этим пользователем");
    const confirmed = window.confirm(`Удалить чат с пользователем "${peerLabel}"?`);
    if (!confirmed) {
      return;
    }

    try {
      await api.deleteChat(chatId);
      removeChatFromState(chatId);
      setNotice("Чат удалён.");
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        removeChatFromState(chatId);
        setNotice("Чат уже удалён.");
      } else {
        setNotice(toMessageText(error));
      }
    }
  }, [api, getUserDisplayName, removeChatFromState]);

  const handleCloseActiveConversation = () => {
    setActiveChatId(null);
    setActiveDraftPeerUserId(null);
    setComposerValue("");
    setMessageEditDraft(null);
    setReplyToMessage(null);
    setGroupInfoOpen(false);
    setChatRenameOpen(false);
  };

  const handleLoadActiveChatParticipants = useCallback(async (chatId: number) => {
    setActiveChatParticipantsLoading(true);
    try {
      const participants = await api.getChatUsers(chatId);
      setActiveChatParticipants(participants);
      ensureUserNamesLoaded(participants.map((p) => p.userId));
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setActiveChatParticipantsLoading(false);
    }
  }, [api, ensureUserNamesLoaded]);

  const handleToggleGroupInfo = useCallback(() => {
    setGroupInfoOpen((prev) => {
      if (!prev && activeChatId !== null) {
        void handleLoadActiveChatParticipants(activeChatId);
      }
      return !prev;
    });
    setChatRenameOpen(false);
  }, [activeChatId, handleLoadActiveChatParticipants]);

  const handleCreateGroup = async () => {
    if (!profile) return;
    const name = groupNameDraft.trim();
    if (!name) { setNotice("Введите название группы."); return; }

    setGroupCreateBusy(true);
    try {
      const participantIds = groupSelectedMembers.map((m) => m.userId);
      const chat = await api.createGroupChat(name, participantIds);
      upsertChat(chat.chatId, null, undefined, chat.chatName);
      setChatTypeById((prev) => ({ ...prev, [chat.chatId]: "GROUP" }));
      setCreateGroupOpen(false);
      setGroupNameDraft("");
      setGroupSelectedMembers([]);
      setGroupSearchTag("");
      setGroupSearchResults([]);
      navigate("/");
      setActiveChatId(chat.chatId);
      setNotice("Группа создана.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setGroupCreateBusy(false);
    }
  };

  const handleAddMemberSubmit = async (userId: number) => {
    if (activeChatId === null) return;
    setAddMemberBusy(true);
    try {
      await api.addUserToChat(activeChatId, userId);
      setNotice("Участник добавлен.");
      setAddMemberOpen(false);
      setAddMemberTag("");
      setAddMemberResults([]);
      if (groupInfoOpen) {
        void handleLoadActiveChatParticipants(activeChatId);
      }
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setAddMemberBusy(false);
    }
  };

  const handleUpdateMemberRole = async (userId: number, role: "OWNER" | "ADMIN" | "MEMBER") => {
    if (activeChatId === null || !profile) return;
    try {
      await api.updateChatUserRole(activeChatId, userId, role);
      setActiveChatParticipants((prev) =>
        prev.map((p) => p.userId === userId ? { ...p, role } : p)
      );
      setNotice("Роль обновлена.");
    } catch (error) {
      setNotice(toMessageText(error));
    }
  };

  const handleMyRename = async () => {
    if (myRenameChatId === null) return;
    const name = myRenameDraft.trim() || null;
    setMyRenameBusy(true);
    try {
      await api.setMyCustomChatName(myRenameChatId, name);
      setKnownChats((prev) => prev.map((c) =>
        // name=null means reset: clear chatName so displayName falls back to peer name / group name
        c.chatId === myRenameChatId ? { ...c, chatName: name ?? "" } : c
      ));
      setMyRenameOpen(false);
      setNotice(name ? "Название изменено для вас." : "Название сброшено.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setMyRenameBusy(false);
    }
  };

  const handleRenameActiveChat = async () => {
    if (activeChatId === null) return;
    const name = chatRenameDraft.trim();
    if (!name) { setNotice("Введите новое название."); return; }
    setChatRenameBusy(true);
    try {
      await api.changeChatName(activeChatId, name);
      upsertChat(activeChatId, null, undefined, name);
      setChatRenameOpen(false);
      setNotice("Название изменено.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setChatRenameBusy(false);
    }
  };

  const handleLeaveChat = async (chatId: number) => {
    const confirmed = window.confirm("Покинуть этот чат?");
    if (!confirmed) return;
    setLeaveChatBusy(true);
    try {
      await api.leaveChat(chatId);
      removeChatFromState(chatId);
      setGroupInfoOpen(false);
      setNotice("Вы покинули чат.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setLeaveChatBusy(false);
    }
  };

  const handleForwardMessage = useCallback((message: ChatMessage) => {
    setMessageMenu(null);
    setForwardModalMessage(message);
  }, []);

  const handleConfirmForward = useCallback(async (targetChatId: number) => {
    const msg = forwardModalMessage;
    if (!msg || !profile) return;
    setForwardModalMessage(null);
    const content = msg.content || "";
    const photoLinks = msg.photoLinks && msg.photoLinks.length > 0 ? msg.photoLinks : null;
    try {
      await api.sendMessage(targetChatId, content, null, photoLinks);
      setNotice("Сообщение переслано.");
    } catch {
      setNotice("Не удалось переслать сообщение.");
    }
  }, [api, forwardModalMessage, profile]);

  const handleGroupAvatarUpload = async (file: File) => {
    if (!activeChatId) return;
    setGroupAvatarUploading(true);
    try {
      const { url } = await api.uploadMedia(file);
      await api.updateChatAvatar(activeChatId, url);
      setKnownChats((prev) => prev.map((c) => c.chatId === activeChatId ? { ...c, avatarUrl: url } : c));
      setNotice("Аватар группы обновлён.");
    } catch {
      setNotice("Не удалось обновить аватар группы.");
    } finally {
      setGroupAvatarUploading(false);
    }
  };

  const handleGroupAvatarRemove = async () => {
    if (!activeChatId) return;
    setGroupAvatarUploading(true);
    try {
      await api.updateChatAvatar(activeChatId, null);
      setKnownChats((prev) => prev.map((c) => c.chatId === activeChatId ? { ...c, avatarUrl: null } : c));
      setNotice("Аватар группы удалён.");
    } catch {
      setNotice("Не удалось удалить аватар группы.");
    } finally {
      setGroupAvatarUploading(false);
    }
  };

  const handleAvatarUpload = async (file: File) => {
    if (!profile) return;
    setAvatarUploading(true);
    try {
      const { url } = await api.uploadMedia(file);
      const updated = await api.updateAvatar(url);
      setProfile(updated);
      setNotice("Аватар обновлён.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setAvatarUploading(false);
    }
  };

  const handleAvatarRemove = async () => {
    if (!profile) return;
    setAvatarUploading(true);
    try {
      const updated = await api.updateAvatar(null);
      setProfile(updated);
      setNotice("Аватар удалён.");
    } catch (error) {
      setNotice(toMessageText(error));
    } finally {
      setAvatarUploading(false);
    }
  };

  const dragCounterRef = useRef(0);

  const handleChatDragEnter = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    dragCounterRef.current += 1;
    if (event.dataTransfer.types.includes("Files")) {
      setIsDraggingOver(true);
    }
  }, []);

  const handleChatDragLeave = useCallback(() => {
    dragCounterRef.current -= 1;
    if (dragCounterRef.current <= 0) {
      dragCounterRef.current = 0;
      setIsDraggingOver(false);
    }
  }, []);

  const handleChatDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
  }, []);

  const handleChatDrop = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    dragCounterRef.current = 0;
    setIsDraggingOver(false);
    if (messageEditDraft) return;
    const files = Array.from(event.dataTransfer.files);
    if (files.length === 0) return;
    setPendingFiles((prev) => {
      const currentImages = prev.filter((f) => f.type.startsWith("image/")).length;
      const newImages = files.filter((f) => f.type.startsWith("image/"));
      const newOther = files.filter((f) => !f.type.startsWith("image/"));
      const canAdd = Math.max(0, 10 - currentImages);
      if (newImages.length > canAdd) setNotice("Максимум 10 фото в одном сообщении");
      return [...prev, ...newImages.slice(0, canAdd), ...newOther];
    });
    composerTextareaRef.current?.focus();
  }, [messageEditDraft]);

  const resizeTextarea = useCallback(() => {
    const el = composerTextareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  }, []);

  const handleComposerChange = useCallback((event: React.ChangeEvent<HTMLTextAreaElement>) => {
    setComposerValue(event.target.value);
    resizeTextarea();
    // Typing indicator: send at most once per TYPING_DEBOUNCE_MS
    const chatId = activeChatIdRef.current;
    if (chatId !== null && event.target.value.length > 0) {
      const now = Date.now();
      if (now - lastTypingSentRef.current > TYPING_DEBOUNCE_MS) {
        lastTypingSentRef.current = now;
        realtimeRef.current?.sendTyping(chatId);
      }
    }
  }, [resizeTextarea]);

  const handleComposerPaste = useCallback((event: React.ClipboardEvent<HTMLTextAreaElement>) => {
    if (messageEditDraft) return;
    const images = Array.from(event.clipboardData.items)
      .filter((item) => item.kind === "file" && item.type.startsWith("image/"))
      .map((item) => item.getAsFile())
      .filter((f): f is File => f !== null);
    if (images.length === 0) return;
    event.preventDefault();
    setPendingFiles((prev) => {
      const currentImages = prev.filter((f) => f.type.startsWith("image/")).length;
      const canAdd = Math.max(0, 10 - currentImages);
      if (images.length > canAdd) setNotice("Максимум 10 фото в одном сообщении");
      return [...prev, ...images.slice(0, canAdd)];
    });
  }, [messageEditDraft]);

  const handleSetReplyTo = useCallback((message: ChatMessage) => {
    setReplyToMessage(message);
    setMessageMenu(null);
    requestAnimationFrame(() => {
      composerTextareaRef.current?.focus();
    });
  }, []);

  const handleCancelReply = useCallback(() => {
    setReplyToMessage(null);
  }, []);

  const handleSendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!profile || !hasActiveConversation) {
      return;
    }

    const content = composerValue.trim();
    const filesToUpload = messageEditDraft ? [] : [...pendingFiles];

    if (!content && filesToUpload.length === 0) {
      return;
    }

    if (messageEditDraft) {
      if (activeChatId === null || activeChatId !== messageEditDraft.chatId) {
        setMessageEditDraft(null);
        setNotice("Выберите чат с редактируемым сообщением.");
        return;
      }

      const targetMessage = currentMessages.find((message) => message.id === messageEditDraft.messageId);
      if (!targetMessage) {
        setMessageEditDraft(null);
        setNotice("Сообщение для редактирования не найдено.");
        return;
      }

      if (targetMessage.userId !== profile.userId) {
        setMessageEditDraft(null);
        setNotice("Можно редактировать только свои сообщения.");
        return;
      }

      if (content === targetMessage.content.trim()) {
        setNotice("Текст сообщения не изменился.");
        return;
      }

      try {
        let serverId = normalizeServerId(targetMessage.serverId);
        if (!serverId) {
          serverId = await resolveServerIdForMessage(targetMessage);
          if (serverId) {
            updateMessageServerId(targetMessage.chatId, targetMessage.id, serverId);
          }
        }

        if (!serverId) {
          setNotice("Не удалось определить ID сообщения для редактирования. Обновите чат и попробуйте снова.");
          return;
        }

        const updated = await api.editMessage(serverId, content);
        const updatedServerId = normalizeServerId(updated.id ?? undefined);
        if (updatedServerId) {
          updateMessageServerId(targetMessage.chatId, targetMessage.id, updatedServerId);
        }

        updateMessageContentById(
          targetMessage.chatId,
          targetMessage.id,
          updated.content,
          Boolean(updated.editStatus)
        );
        setMessageEditDraft(null);
        setComposerValue("");
        setNotice("Сообщение изменено.");
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          removeMessageFromState(targetMessage.chatId, targetMessage.id);
          setMessageEditDraft(null);
          setComposerValue("");
          setNotice("Сообщение уже удалено.");
        } else {
          setNotice(toMessageText(error));
        }
      }
      return;
    }

    let targetChatId = activeChatId;
    let targetPeerUserId: number | null = activePeerUserId;

    if (targetChatId === null) {
      const draftPeerUserId = activeDraftPeerUserId;
      if (draftPeerUserId === null) {
        return;
      }

      setCreateChatBusy(true);
      try {
        const peerDisplayName = getUserDisplayName(draftPeerUserId);
        const chatName = peerDisplayName !== "Неизвестный пользователь" ? peerDisplayName : String(draftPeerUserId);
        const chat = await api.createChat(draftPeerUserId, chatName);
        targetChatId = chat.chatId;
        targetPeerUserId = draftPeerUserId;
        upsertChat(chat.chatId, draftPeerUserId, undefined, chat.chatName);
      } catch (error) {
        setNotice(toMessageText(error));
        return;
      } finally {
        setCreateChatBusy(false);
      }

      if (targetChatId === null) {
        setNotice("Не удалось создать чат.");
        return;
      }

      setActiveChatId(targetChatId);
      setActiveDraftPeerUserId(null);
    }
    if (targetChatId === null) {
      return;
    }
    const finalChatId = targetChatId;

    const blobUrls = filesToUpload.map((f) => URL.createObjectURL(f));

    const capturedReplyTo = replyToMessage;
    const capturedRepliedId = capturedReplyTo
      ? (normalizeServerId(capturedReplyTo.serverId) ?? null)
      : null;

    const messageId = makeLocalMessageId();
    const sendAt = formatLocalDateTime(Date.now());

    const timeline = timelineRef.current;
    const shouldAutoScroll = timeline ? isNearBottom(timeline) : stickToBottomRef.current;

    appendMessage({
      id: messageId,
      chatId: finalChatId,
      userId: profile.userId,
      content,
      createdAt: sendAt,
      delivery: "pending",
      origin: "local",
      repliedMessage: capturedReplyTo
        ? {
            id: capturedRepliedId ?? capturedReplyTo.id,
            userId: capturedReplyTo.userId,
            content: capturedReplyTo.content,
            sendAt: capturedReplyTo.createdAt
          }
        : null,
      photoLinks: blobUrls.length > 0 ? blobUrls : null
    });

    upsertChat(finalChatId, targetPeerUserId ?? null, sendAt, undefined, {
      text: content || null,
      userId: profile.userId,
      hasMedia: blobUrls.length > 0
    });
    setComposerValue("");
    setPendingFiles([]);
    setReplyToMessage(null);
    setEmojiPickerOpen(false);
    lastTypingSentRef.current = 0;
    requestAnimationFrame(() => {
      if (composerTextareaRef.current) {
        composerTextareaRef.current.style.height = "auto";
      }
    });
    if (shouldAutoScroll) {
      stickToBottomRef.current = true;
      requestAnimationFrame(() => {
        scrollToBottom("smooth");
      });
    }

    void (async () => {
      let uploadedPhotoLinks: string[] = [];
      if (filesToUpload.length > 0) {
        try {
          const uploads = await Promise.all(filesToUpload.map((file) => api.uploadMedia(file)));
          uploadedPhotoLinks = uploads.map((r) => r.url);
          uploadedPhotoLinks.forEach((filename, i) => {
            if (blobUrls[i]) mediaBlobCache.set(filename, blobUrls[i]);
          });
          updateMessagePhotoLinks(finalChatId, messageId, uploadedPhotoLinks);
        } catch (error) {
          blobUrls.forEach((u) => URL.revokeObjectURL(u));
          updateMessageDelivery(finalChatId, messageId, "failed");
          setNotice(toMessageText(error));
          return;
        }
      }

      await submitMessage(
        finalChatId,
        content,
        messageId,
        capturedRepliedId,
        uploadedPhotoLinks.length > 0 ? uploadedPhotoLinks : null
      );
    })();
  };

  const handleRetryMessage = async (message: ChatMessage) => {
    updateMessageDelivery(message.chatId, message.id, "pending");
    const repliedId = message.repliedMessage?.id ?? null;
    await submitMessage(message.chatId, message.content, message.id, repliedId, message.photoLinks ?? null);
  };

  const handleDeleteMessage = async (message: ChatMessage) => {
    if (!profile) {
      return;
    }

    if (message.userId !== profile.userId) {
      setNotice("Можно удалять только свои сообщения.");
      return;
    }

    setDeleteMessageBusyById((previous) => ({
      ...previous,
      [message.id]: true
    }));

    try {
      let serverId = normalizeServerId(message.serverId);
      if (!serverId) {
        serverId = await resolveServerIdForMessage(message);
        if (serverId) {
          updateMessageServerId(message.chatId, message.id, serverId);
        }
      }

      if (!serverId) {
        setNotice("Не удалось определить ID сообщения для удаления. Обновите чат и попробуйте снова.");
        return;
      }

      await api.deleteMessage(serverId);
      removeMessageFromState(message.chatId, message.id);
      setNotice("Сообщение удалено.");
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        removeMessageFromState(message.chatId, message.id);
        setNotice("Сообщение уже удалено.");
      } else {
        setNotice(toMessageText(error));
      }
    } finally {
      setDeleteMessageBusyById((previous) => {
        if (!previous[message.id]) {
          return previous;
        }

        const next = { ...previous };
        delete next[message.id];
        return next;
      });
    }
  };

  const handleStartEditingMessage = (message: ChatMessage) => {
    if (!profile) {
      return;
    }

    if (message.userId !== profile.userId) {
      setNotice("Можно редактировать только свои сообщения.");
      return;
    }

    if (message.delivery === "pending" || message.delivery === "failed") {
      setNotice("Нельзя редактировать сообщение, которое ещё не отправлено.");
      return;
    }

    setComposerValue(message.content);
    setMessageEditDraft({
      chatId: message.chatId,
      messageId: message.id
    });
    requestAnimationFrame(() => {
      const input = composerTextareaRef.current;
      if (!input) return;
      input.style.height = "auto";
      input.style.height = `${Math.min(input.scrollHeight, 200)}px`;
      input.focus();
      input.setSelectionRange(input.value.length, input.value.length);
    });
  };

  const handleCancelMessageEdit = () => {
    setMessageEditDraft(null);
    setComposerValue("");
    requestAnimationFrame(() => {
      if (composerTextareaRef.current) composerTextareaRef.current.style.height = "auto";
    });
  };

  const handleToggleReaction = async (message: ChatMessage, reactionType: string) => {
    if (!profile) return;
    const serverId = normalizeServerId(message.serverId);
    if (!serverId) {
      setNotice("Нельзя поставить реакцию на неотправленное сообщение.");
      return;
    }
    const chat = knownChats.find((c) => c.chatId === message.chatId);
    if (!chat) return;

    const currentReactions = reactionsByMessageId[serverId] ?? [];
    const currentMyReactions = myReactionsByMessageId[serverId] ?? [];
    const alreadyReacted = currentReactions.some(
      (reaction) => reaction.reactionType === reactionType && (reaction.reactedByCurrentUser || currentMyReactions.includes(reaction.reactionType))
    );

    if (alreadyReacted) {
      setMyReactionsByMessageId((prev) => ({
        ...prev,
        [serverId]: (prev[serverId] ?? []).filter((reaction) => reaction !== reactionType)
      }));
      setReactionsByMessageId((prev) => {
        const current = prev[serverId] ?? [];
        const existing = current.find((r) => r.reactionType === reactionType);
        if (!existing) return prev;
        const next = existing.count <= 1
          ? current.filter((r) => r.reactionType !== reactionType)
          : current.map((r) =>
            r.reactionType === reactionType ? { ...r, count: r.count - 1, reactedByCurrentUser: false } : r
          );
        return { ...prev, [serverId]: next };
      });
      try {
        await api.deleteReaction(serverId, reactionType);
      } catch (err) {
        setMyReactionsByMessageId((prev) => ({
          ...prev,
          [serverId]: Array.from(new Set([...(prev[serverId] ?? []), reactionType]))
        }));
        setNotice(err instanceof ApiError ? `Не удалось убрать реакцию (${err.status}: ${err.message})` : "Не удалось убрать реакцию: нет соединения с сервером.");
      }
    } else {
      setMyReactionsByMessageId((prev) => ({
        ...prev,
        [serverId]: Array.from(new Set([...(prev[serverId] ?? []), reactionType]))
      }));
      setReactionsByMessageId((prev) => {
        const current = prev[serverId] ?? [];
        const existing = current.find((r) => r.reactionType === reactionType);
        const next = existing
          ? current.map((r) =>
            r.reactionType === reactionType ? { ...r, count: r.count + 1, reactedByCurrentUser: true } : r
          )
          : [...current, { reactionType, count: 1, reactedByCurrentUser: true }];
        return { ...prev, [serverId]: next };
      });
      try {
        await api.addReaction(chat.chatId, serverId, reactionType);
      } catch (err) {
        setMyReactionsByMessageId((prev) => ({
          ...prev,
          [serverId]: (prev[serverId] ?? []).filter((reaction) => reaction !== reactionType)
        }));
        setReactionsByMessageId((prev) => {
          const current = prev[serverId] ?? [];
          const existing = current.find((r) => r.reactionType === reactionType);
          if (!existing) return prev;
          const next = existing.count <= 1
            ? current.filter((r) => r.reactionType !== reactionType)
            : current.map((r) =>
              r.reactionType === reactionType ? { ...r, count: r.count - 1, reactedByCurrentUser: false } : r
            );
          return { ...prev, [serverId]: next };
        });
        setNotice(err instanceof ApiError ? `Не удалось поставить реакцию (${err.status}: ${err.message})` : "Не удалось поставить реакцию: нет соединения с сервером.");
      }
    }
    setMessageMenu(null);
  };

  const openMessageMenuAt = useCallback((x: number, y: number, messageId: string) => {
    const menuWidth = 196;
    const menuHeight = 206;
    const nextX = Math.max(8, Math.min(x, window.innerWidth - menuWidth));
    const nextY = Math.max(8, Math.min(y, window.innerHeight - menuHeight));
    setChatMenu(null);
    setMessageMenu({
      messageId,
      x: nextX,
      y: nextY
    });
  }, []);

  const openChatMenuAt = useCallback((x: number, y: number, chatId: number) => {
    const menuWidth = 220;
    const menuHeight = 176;
    const nextX = Math.max(8, Math.min(x, window.innerWidth - menuWidth));
    const nextY = Math.max(8, Math.min(y, window.innerHeight - menuHeight));
    setMessageMenu(null);
    setChatMenu({
      chatId,
      x: nextX,
      y: nextY
    });
  }, []);

  const clearLongPress = useCallback(() => {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }

    longPressPointRef.current = null;
    longPressTargetRef.current = null;
  }, []);

  const fireLongPress = useCallback(() => {
    const point = longPressPointRef.current;
    const target = longPressTargetRef.current;
    clearLongPress();

    if (!point || !target) {
      return;
    }

    if (target.kind === "chat") {
      suppressNextTapRef.current = true;
      openChatMenuAt(point.x, point.y, target.chatId);
      return;
    }

    openMessageMenuAt(point.x, point.y, target.messageId);
  }, [clearLongPress, openChatMenuAt, openMessageMenuAt]);

  const startLongPress = useCallback((event: ReactTouchEvent<HTMLElement>, target: LongPressTarget) => {
    if (event.touches.length !== 1) {
      clearLongPress();
      return;
    }

    const touch = event.touches[0];
    longPressPointRef.current = { x: touch.clientX, y: touch.clientY };
    longPressTargetRef.current = target;

    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
    }

    longPressTimerRef.current = window.setTimeout(() => {
      fireLongPress();
    }, CONTEXT_LONG_PRESS_MS);
  }, [clearLongPress, fireLongPress]);

  const moveLongPress = useCallback((event: ReactTouchEvent<HTMLElement>) => {
    if (longPressTimerRef.current === null || !longPressPointRef.current || event.touches.length !== 1) {
      return;
    }

    const touch = event.touches[0];
    const distanceX = Math.abs(touch.clientX - longPressPointRef.current.x);
    const distanceY = Math.abs(touch.clientY - longPressPointRef.current.y);
    if (distanceX > CONTEXT_LONG_PRESS_MOVE_PX || distanceY > CONTEXT_LONG_PRESS_MOVE_PX) {
      clearLongPress();
    }
  }, [clearLongPress]);

  useEffect(() => () => {
    clearLongPress();
  }, [clearLongPress]);

  useEffect(() => () => {
    for (const timerId of Object.values(presenceOfflineTimersRef.current)) {
      window.clearTimeout(timerId);
    }
    presenceOfflineTimersRef.current = {};
  }, []);

  const handleMessageContextMenu = (event: ReactMouseEvent<HTMLElement>, message: ChatMessage) => {
    event.preventDefault();
    openMessageMenuAt(event.clientX, event.clientY, message.id);
  };

  const handleChatContextMenu = (event: ReactMouseEvent<HTMLElement>, chatId: number) => {
    event.preventDefault();
    openChatMenuAt(event.clientX, event.clientY, chatId);
  };

  const handleCopyMessage = async (message: ChatMessage) => {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(message.content);
        setNotice("Текст скопирован.");
        return;
      }
    } catch {
      // Continue to legacy fallback.
    }

    try {
      const fallback = document.createElement("textarea");
      fallback.value = message.content;
      fallback.setAttribute("readonly", "true");
      fallback.style.position = "fixed";
      fallback.style.left = "-9999px";
      document.body.appendChild(fallback);
      fallback.select();
      document.execCommand("copy");
      document.body.removeChild(fallback);
      setNotice("Текст скопирован.");
    } catch {
      setNotice("Не удалось скопировать текст.");
    }
  };

  const wsStatusLabel = STATUS_LABELS[wsStatus];
  const activePeerStatusLabel = `Собеседник: ${resolvePeerPresenceLabel(activePeerPresenceState)}`;
  const authMode: AuthMode = route === "/register" ? "register" : "login";
  const isSearchRoute = route === "/search";
  const isProfileRoute = route === "/profile";
  const isSettingsSectionRoute = isSettingsRoute(route);
  const isSettingsHomeRoute = route === "/settings";
  const isSettingsProfileRoute = route === "/settings/profile";
  const isSettingsProfileEditRoute = route === "/settings/profile/edit";
  const isSettingsAppearanceRoute = route === "/settings/appearance";
  const isChatRoute = route === "/";
  const canSubmitMessage = (composerValue.trim().length > 0 || pendingFiles.length > 0) && (!createChatBusy || isEditingMessage);

  if (!tokensState) {
    return (
      <div className="tg-page">
        <main className="tg-auth-shell">
          <section className="tg-auth-hero">
            <p className="tg-label">Мессенджер</p>
            <h1>Удобный мессенджер для вашей команды.</h1>
            <p>
              В одном месте доступны вход, личные чаты и настройки аккаунта. Общайтесь в реальном времени без
              лишних технических деталей.
            </p>
          </section>

          <section className="tg-auth-card">
            <div className="tg-mode-switch">
              <button
                type="button"
                className={authMode === "login" ? "active" : ""}
                onClick={() => navigate("/login")}
              >
                Вход
              </button>
              <button
                type="button"
                className={authMode === "register" ? "active" : ""}
                onClick={() => navigate("/register")}
              >
                Регистрация
              </button>
            </div>

            {authMode === "login" ? (
              <form className="tg-form" onSubmit={handleLoginSubmit}>
                <label>
                  Почта
                  <input
                    type="email"
                    value={loginEmail}
                    onChange={(event) => setLoginEmail(event.target.value)}
                    required
                  />
                </label>
                <label>
                  Пароль
                  <input
                    type="password"
                    minLength={8}
                    value={loginPassword}
                    onChange={(event) => setLoginPassword(event.target.value)}
                    required
                  />
                </label>
                <button type="submit" className="primary" disabled={authBusy}>
                  {authBusy ? "Вход..." : "Войти"}
                </button>
              </form>
            ) : (
              <form className="tg-form" onSubmit={handleRegisterSubmit}>
                <label>
                  Почта
                  <input
                    type="email"
                    value={registerEmail}
                    onChange={(event) => setRegisterEmail(event.target.value)}
                    required
                  />
                </label>
                <label>
                  Пароль
                  <input
                    type="password"
                    minLength={8}
                    value={registerPassword}
                    onChange={(event) => setRegisterPassword(event.target.value)}
                    required
                  />
                </label>
                <label>
                  Отображаемое имя
                  <input
                    type="text"
                    minLength={4}
                    maxLength={50}
                    value={registerName}
                    onChange={(event) => setRegisterName(event.target.value)}
                    required
                  />
                </label>
                <label>
                  Тег
                  <input
                    type="text"
                    minLength={3}
                    maxLength={15}
                    value={registerTag}
                    onChange={(event) => setRegisterTag(event.target.value)}
                    placeholder="например, alex"
                    required
                  />
                </label>
                <label>
                  О себе
                  <input
                    type="text"
                    maxLength={50}
                    value={registerDescription}
                    onChange={(event) => setRegisterDescription(event.target.value)}
                  />
                </label>
                <button type="submit" className="primary" disabled={authBusy}>
                  {authBusy ? "Создание аккаунта..." : "Создать аккаунт"}
                </button>
              </form>
            )}
          </section>
        </main>

        {notice && <div className="tg-toast">{notice}</div>}
      </div>
    );
  }

  if (profileLoading && !profile) {
    return (
      <div className="tg-page">
        <main className="tg-loading-shell">
          <section className="tg-loading-card">
            <h2>Загрузка профиля</h2>
            <p>Подготавливаем ваш профиль и список чатов.</p>
            <button type="button" className="ghost" onClick={handleLogout}>
              Выйти
            </button>
          </section>
        </main>

        {notice && <div className="tg-toast">{notice}</div>}
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="tg-page">
        <main className="tg-loading-shell">
          <section className="tg-loading-card">
            <h2>Не удалось загрузить профиль</h2>
            <p>{profileError ?? "Ошибка запроса профиля. Повторите попытку или выйдите из аккаунта."}</p>
            <button type="button" className="secondary" onClick={handleRetryProfileLoad}>
              Повторить
            </button>
            <button type="button" className="ghost" onClick={handleLogout}>
              Выйти
            </button>
          </section>
        </main>

        {notice && <div className="tg-toast">{notice}</div>}
      </div>
    );
  }

  return (
    <div className="tg-page">
      <main
        className={isSettingsSectionRoute
          ? "tg-shell tg-shell-single"
          : isProfileRoute
            ? "tg-shell tg-shell-profile-route"
            : (isChatRoute && hasActiveConversation ? "tg-shell tg-shell-chat-route tg-shell-chat-open" : (isChatRoute ? "tg-shell tg-shell-chat-route" : "tg-shell"))}
      >
        {!isSettingsSectionRoute && (
          <aside className="tg-sidebar">
          <section className="tg-card tg-sidebar-panel">
            <div className="tg-sidebar-top">
              <div className="tg-profile-head">
                <div className="tg-profile-identity">
                  <div className="tg-profile-name">
                    <button
                      type="button"
                      className="tg-name-link"
                      onClick={() => {
                        setProfileViewUserId(profile.userId);
                        navigate("/profile");
                      }}
                    >
                      {profile.name}
                    </button>
                    <span
                      className={`tg-presence-dot tg-presence-${wsStatus}`}
                      title={wsStatusLabel}
                      aria-label={wsStatusLabel}
                    />
                  </div>
                  <p className="tg-profile-tag">@{profile.tag}</p>
                </div>
                <div className="tg-sidebar-actions">
                  {!isSettingsSectionRoute && (
                    <button
                      type="button"
                      className="tg-new-group-btn"
                      onClick={() => {
                        setGroupNameDraft("");
                        setGroupSelectedMembers([]);
                        setGroupSearchTag("");
                        setGroupSearchResults([]);
                        setCreateGroupOpen(true);
                      }}
                      aria-label="Создать группу"
                      title="Создать группу"
                    >
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                        <circle cx="9" cy="7" r="4" />
                        <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                        <path d="M21 12v6M18 15h6" />
                      </svg>
                    </button>
                  )}
                  {!isSettingsSectionRoute && (
                    <button
                      type="button"
                      className="tg-sidebar-action-btn"
                      onClick={() => navigate("/search")}
                      aria-label="Найти пользователя"
                      title="Найти пользователя"
                    >
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <circle cx="11" cy="11" r="7" />
                        <path d="m21 21-4.35-4.35" />
                      </svg>
                    </button>
                  )}
                  <button
                    type="button"
                    className="tg-sidebar-action-btn"
                    onClick={() => navigate("/settings")}
                    aria-label="Открыть настройки"
                    title="Открыть настройки"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M4.5 12a7.5 7.5 0 0 1 .16-1.54L2.9 9.1l1.8-3.2 2.15.63a7.7 7.7 0 0 1 2.66-1.54L9.9 2.7h4.2l.39 2.29a7.7 7.7 0 0 1 2.66 1.54l2.15-.63 1.8 3.2-1.76 1.36c.11.5.16 1.02.16 1.54s-.05 1.04-.16 1.54l1.76 1.36-1.8 3.2-2.15-.63a7.7 7.7 0 0 1-2.66 1.54l-.39 2.29H9.9l-.39-2.29a7.7 7.7 0 0 1-2.66-1.54l-2.15.63-1.8-3.2 1.76-1.36c-.11-.5-.16-1.02-.16-1.54Z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>

            <div className="tg-sidebar-divider" />

            {!isSettingsSectionRoute ? (
              <>
                <section className="tg-sidebar-section tg-chat-list-card">
                  <div className="tg-chat-list">
                    {sortedChats.length === 0 ? (
                      <p className="tg-meta" style={{ padding: "0.5rem 0.4rem" }}>Пока нет чатов.</p>
                    ) : (
                      sortedChats.map((chat) => {
                        const knownType = chatTypeById[chat.chatId];
                        const displayName = chat.chatName
                          || (knownType === "PRIVATE" && chat.peerUserId ? getUserDisplayName(chat.peerUserId) : "Чат");
                        // Only show presence dot for confirmed PRIVATE chats (never for groups).
                        const presenceState = knownType === "PRIVATE" && chat.peerUserId !== null
                          ? resolvePeerPresenceState(chat.peerUserId)
                          : null;
                        const chatAvatarSeed = chat.peerUserId ?? chat.chatId;
                        const chatAvatarUrl = chat.peerUserId != null
                          ? (avatarUrlByUserId[chat.peerUserId] ?? undefined)
                          : (chat.avatarUrl ?? undefined);
                        // Only show "группа" when type is confirmed, never during load.
                        const isConfirmedGroup = chatTypeById[chat.chatId] === "GROUP";
                        const unread = unreadByChatId[chat.chatId] ?? 0;
                        const ts = chat.updatedAt !== "1970-01-01T00:00:00.000"
                          ? formatChatTimestamp(chat.updatedAt)
                          : "";

                        // Last message preview: prefer live data from messagesByChat, fall back to server preview
                        const liveMsgs = messagesByChat[String(chat.chatId)];
                        const lastMsg = liveMsgs && liveMsgs.length > 0 ? liveMsgs[liveMsgs.length - 1] : null;
                        const previewText = lastMsg
                          ? (lastMsg.content || (lastMsg.photoLinks && lastMsg.photoLinks.length > 0 ? "📎 Фото" : null))
                          : (chat.lastMessageHasMedia && !chat.lastMessagePreview ? "📎 Фото" : (chat.lastMessagePreview ?? null));
                        const previewUserId = lastMsg ? lastMsg.userId : (chat.lastMessageUserId ?? null);
                        const isMinePreview = previewUserId !== null && previewUserId === profile.userId;

                        const chatTypingUsers = Object.keys(typingByChatId[chat.chatId] ?? {})
                          .map(Number)
                          .filter((uid) => uid !== profile.userId);

                        return (
                          <div
                            key={chat.chatId}
                            className={chat.chatId === activeChatId ? "tg-chat-row active" : "tg-chat-row"}
                            onContextMenu={(event) => handleChatContextMenu(event, chat.chatId)}
                            onTouchStart={(event) => startLongPress(event, { kind: "chat", chatId: chat.chatId })}
                            onTouchMove={moveLongPress}
                            onTouchEnd={clearLongPress}
                            onTouchCancel={clearLongPress}
                          >
                            <button
                              type="button"
                              className="tg-chat-row-main"
                              onClick={() => {
                                if (suppressNextTapRef.current) {
                                  suppressNextTapRef.current = false;
                                  return;
                                }
                                navigate("/");
                                setActiveDraftPeerUserId(null);
                                setActiveChatId(chat.chatId);
                                setGroupInfoOpen(false);
                                setChatRenameOpen(false);
                              }}
                            >
                              <AvatarImage
                                name={displayName}
                                seed={chatAvatarSeed}
                                avatarUrl={chatAvatarUrl}
                                fetchMedia={api.fetchMediaBlob}
                              >
                                {presenceState && presenceState !== "offline" && (
                                  <span className={`tg-avatar-presence ${presenceState}`} />
                                )}
                              </AvatarImage>
                              <div className="tg-chat-row-body">
                                <div className="tg-chat-row-top">
                                  <span className="tg-chat-row-name">{displayName}</span>
                                  {ts && <span className="tg-chat-row-time">{ts}</span>}
                                </div>
                                <div className="tg-chat-row-bottom">
                                  <span className="tg-chat-row-preview">
                                    {chatTypingUsers.length > 0 ? (() => {
                                      const typingLabel = isConfirmedGroup
                                        ? (() => {
                                            const names = chatTypingUsers.slice(0, 2).map((uid) => getUserDisplayName(uid));
                                            return chatTypingUsers.length === 1
                                              ? `${names[0]} печатает`
                                              : chatTypingUsers.length === 2
                                                ? `${names[0]} и ${names[1]} печатают`
                                                : "Несколько человек печатают";
                                          })()
                                        : "печатает";
                                      return (
                                        <span className="tg-typing-indicator">
                                          <span className="tg-typing-dots"><span /><span /><span /></span>
                                          {typingLabel}
                                        </span>
                                      );
                                    })() : previewText ? (
                                      <>
                                        {isMinePreview && <span className="tg-preview-you">Вы: </span>}
                                        {previewText}
                                      </>
                                    ) : isConfirmedGroup ? (
                                      <span className="tg-preview-muted">группа</span>
                                    ) : null}
                                  </span>
                                  {unread > 0 && (
                                    <span className="tg-chat-unread" aria-label={`Непрочитанных: ${unread}`}>
                                      {unread > 99 ? "99+" : unread}
                                    </span>
                                  )}
                                </div>
                              </div>
                            </button>
                          </div>
                        );
                      })
                    )}
                  </div>
                </section>
              </>
            ) : (
              <section className="tg-sidebar-section tg-settings-note">
                <h3>Настройки</h3>
                <p className="tg-meta">Изменения профиля доступны на отдельной странице настроек.</p>
                <button type="button" className="secondary" onClick={() => navigate("/")}>
                  Вернуться к чатам
                </button>
              </section>
            )}
          </section>
          </aside>
        )}

        {isChatRoute ? (
          <section
            className="tg-chat-pane"
            onDragEnter={handleChatDragEnter}
            onDragLeave={handleChatDragLeave}
            onDragOver={handleChatDragOver}
            onDrop={handleChatDrop}
          >
            {isDraggingOver && !messageEditDraft && (
              <div className="tg-drop-overlay">
                <div className="tg-drop-overlay-inner">
                  <svg viewBox="0 0 24 24" aria-hidden="true" className="tg-drop-icon">
                    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
                  </svg>
                  <p>Перетащите файлы сюда</p>
                </div>
              </div>
            )}
            {(() => {
              const knownChatType = activeChatId !== null ? (chatTypeById[activeChatId] ?? null) : null;
              const isGroup = knownChatType === "GROUP";
              const headName = hasActiveConversation
                ? (activeChat?.chatName || (activePeerUserId ? getUserDisplayName(activePeerUserId) : "Чат"))
                : "Чат не выбран";
              const headSeed = activePeerUserId ?? activeChatId ?? 0;
              const headAvatarUrl = activePeerUserId != null
                ? (avatarUrlByUserId[activePeerUserId] ?? undefined)
                : (activeChat?.avatarUrl ?? undefined);

              return (
                <header className="tg-chat-head">
                  <div className="tg-chat-head-main">
                    {hasActiveConversation && (
                      <button
                        type="button"
                        className="tg-chat-back"
                        onClick={handleCloseActiveConversation}
                        aria-label="Назад"
                        title="Назад"
                      >
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                          <path d="M15 5L8 12L15 19" />
                        </svg>
                      </button>
                    )}

                    {hasActiveConversation && (
                      <AvatarImage
                        name={headName}
                        seed={headSeed}
                        avatarUrl={headAvatarUrl}
                        size="sm"
                        fetchMedia={api.fetchMediaBlob}
                      />
                    )}

                    <div
                      className="tg-chat-head-info"
                      onClick={() => {
                        if (!hasActiveConversation) return;
                        if (isGroup) {
                          handleToggleGroupInfo();
                        } else if (activePeerUserId) {
                          setProfileViewUserId(activePeerUserId);
                          navigate("/profile");
                        }
                      }}
                      role={hasActiveConversation ? "button" : undefined}
                      tabIndex={hasActiveConversation ? 0 : undefined}
                      onKeyDown={(e) => { if (e.key === "Enter") e.currentTarget.click(); }}
                    >
                      <h2 className="tg-chat-title">
                        <span className="tg-chat-title-wrap">
                          <span>{headName}</span>
                          {/* Only show presence dot when chat type is confirmed to avoid flicker */}
                          {knownChatType === "PRIVATE" && activePeerUserId && activePeerPresenceState !== "offline" && (
                            <span
                              className={`tg-peer-presence-dot ${activePeerPresenceState}`}
                              title={activePeerStatusLabel}
                              aria-label={activePeerStatusLabel}
                            />
                          )}
                        </span>
                      </h2>
                      {knownChatType !== null && (
                        <div className="tg-chat-subtitle">
                          {(() => {
                            const typingUsers = activeChatId !== null
                              ? Object.keys(typingByChatId[activeChatId] ?? {}).map(Number)
                              : [];
                            if (typingUsers.length > 0) {
                              const names = typingUsers.slice(0, 2).map((uid) => getUserDisplayName(uid));
                              const label = typingUsers.length === 1
                                ? `${names[0]} печатает...`
                                : typingUsers.length === 2
                                  ? `${names[0]} и ${names[1]} печатают...`
                                  : "Несколько человек печатают...";
                              return <span className="tg-typing-indicator"><span className="tg-typing-dots"><span/><span/><span/></span>{label}</span>;
                            }
                            if (isGroup) return <span>группа · нажмите для участников</span>;
                            if (activePeerUserId) return (
                              <span className={activePeerPresenceState === "online" ? "tg-chat-subtitle-online" : ""}>
                                {activePeerPresenceState === "online" ? "в сети" : "не в сети"}
                              </span>
                            );
                            return null;
                          })()}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Info button removed — click header title to open group info */}
                </header>
              );
            })()}

            {hasActiveConversation ? (
              <div className="tg-chat-pane-inner">
                {/* Rename strip for groups */}
                {chatRenameOpen && activeChatId !== null && (
                  <div className="tg-rename-strip">
                    <input
                      type="text"
                      maxLength={63}
                      value={chatRenameDraft}
                      onChange={(e) => setChatRenameDraft(e.target.value)}
                      placeholder="Новое название группы..."
                      autoFocus
                      onKeyDown={(e) => {
                        if (e.key === "Enter") void handleRenameActiveChat();
                        if (e.key === "Escape") setChatRenameOpen(false);
                      }}
                    />
                    <button type="button" className="primary" onClick={() => void handleRenameActiveChat()} disabled={chatRenameBusy}>
                      {chatRenameBusy ? "..." : "OK"}
                    </button>
                    <button type="button" className="ghost" onClick={() => setChatRenameOpen(false)}>Отмена</button>
                  </div>
                )}
                <div className="tg-timeline" ref={timelineRef} onScroll={handleTimelineScroll}>
                  {activePagination.loading && activePagination.initialized && (
                    <div className="tg-history-loader">Загрузка более ранних сообщений...</div>
                  )}

                  {!activePagination.hasMore && currentMessages.length > 0 && (
                    <div className="tg-history-limit">Начало истории чата</div>
                  )}

                  {!activePagination.initialized && activePagination.loading && currentMessages.length === 0 ? (
                    <div className="tg-empty-state">
                      <h3>Загрузка истории</h3>
                      <p>Получаем историю переписки...</p>
                    </div>
                  ) : currentMessages.length === 0 ? (
                    <div className="tg-empty-state">
                      <h3>Сообщений пока нет</h3>
                      <p>Отправьте первое сообщение в этом чате.</p>
                    </div>
                  ) : (
                    timelineRows.map((row) => {
                      if (row.kind === "day") {
                        return <div className="tg-day-separator" key={row.key}>{row.label}</div>;
                      }

                      const { message } = row;
                      const mine = message.userId === profile.userId || message.origin === "local";
                      const dbMessageTime = formatDbMessageTime(message);
                      const msgServerId = normalizeServerId(message.serverId);
                      const msgReactionsRaw = msgServerId ? reactionsByMessageId[msgServerId] : undefined;
                      const msgReactions = Array.isArray(msgReactionsRaw) ? msgReactionsRaw : [];
                      const myReactionTypes = msgServerId ? (myReactionsByMessageId[msgServerId] ?? []) : [];
                      const isGroupChat = activeChatId !== null && (chatTypeById[activeChatId] ?? null) === "GROUP";
                      const senderName = (!mine && isGroupChat) ? getUserDisplayName(message.userId) : null;
                      const senderColor = senderName ? avatarColor(message.userId) : undefined;
                      return (
                        <article
                          className={mine ? "tg-bubble mine" : "tg-bubble remote"}
                          key={row.key}
                          onContextMenu={(event) => handleMessageContextMenu(event, message)}
                          onTouchStart={(event) => startLongPress(event, { kind: "message", messageId: message.id })}
                          onTouchMove={moveLongPress}
                          onTouchEnd={clearLongPress}
                          onTouchCancel={clearLongPress}
                        >
                          {senderName && (
                            <span className="tg-bubble-sender" style={{ color: senderColor }}>
                              {senderName}
                            </span>
                          )}
                          {message.repliedMessage && (
                            <div className="tg-reply-preview">
                              <span className="tg-reply-author">{getUserDisplayName(message.repliedMessage.userId)}</span>
                              <span className="tg-reply-text">{message.repliedMessage.content || "Медиафайл"}</span>
                            </div>
                          )}
                          {message.photoLinks && message.photoLinks.length > 0 && (() => {
                            const validLinks = message.photoLinks.filter((url): url is string => url != null);
                            return (
                              <div
                                className={`tg-message-attachments${deleteMessageBusyById[message.id] ? " deleting" : ""}`}
                                data-count={String(validLinks.length)}
                              >
                                {deleteMessageBusyById[message.id] && (
                                  <div className="tg-delete-overlay">
                                    <span className="tg-image-upload-spinner" />
                                  </div>
                                )}
                                {validLinks.map((url) => (
                                  <AttachmentView key={url} url={url} mine={mine} fetchMedia={api.fetchMediaBlob} />
                                ))}
                              </div>
                            );
                          })()}
                          {message.content && <MessageText content={message.content} />}
                          {msgReactions.length > 0 && (
                            <div className="tg-reactions">
                              {msgReactions.map((r) => (
                                <button
                                  key={r.reactionType}
                                  type="button"
                                  className={`tg-reaction-badge${r.reactedByCurrentUser || myReactionTypes.includes(r.reactionType) ? " mine" : ""}`}
                                  onClick={() => void handleToggleReaction(message, r.reactionType)}
                                  title={r.reactionType}
                                >
                                  {REACTION_EMOJIS[r.reactionType] ?? r.reactionType}
                                  <span className="tg-reaction-count">{r.count}</span>
                                </button>
                              ))}
                            </div>
                          )}
                          <footer>
                            {dbMessageTime && <span>{dbMessageTime}</span>}
                            {message.edited && <span>Изменено</span>}
                            {mine && (
                              <span
                                className={`delivery ${message.delivery}`}
                                title={deliveryLabel(message.delivery)}
                                aria-label={deliveryLabel(message.delivery)}
                              >
                                {renderDeliveryIcon(message.delivery)}
                              </span>
                            )}
                            {mine && message.delivery === "failed" && (
                              <button type="button" className="retry" onClick={() => handleRetryMessage(message)}>
                                Повторить
                              </button>
                            )}
                          </footer>
                        </article>
                      );
                    })
                  )}
                </div>

                <form className="tg-composer" ref={composerFormRef} onSubmit={handleSendMessage}>
                  {replyToMessage && (
                    <div className="tg-composer-strip tg-composer-reply">
                      <svg className="tg-composer-strip-icon" viewBox="0 0 24 24" aria-hidden="true">
                        <polyline points="9 10 4 15 9 20"/>
                        <path d="M20 4v7a4 4 0 0 1-4 4H4"/>
                      </svg>
                      <div className="tg-composer-strip-body">
                        <div className="tg-composer-strip-label">{getUserDisplayName(replyToMessage.userId)}</div>
                        <div className="tg-composer-strip-text">
                          {replyToMessage.photoLinks && replyToMessage.photoLinks.length > 0 && !replyToMessage.content && (
                            <span className="tg-strip-media-hint">📎 Фото</span>
                          )}
                          {replyToMessage.content}
                        </div>
                      </div>
                      <button
                        type="button"
                        className="tg-composer-strip-close"
                        onClick={handleCancelReply}
                        aria-label="Отмена ответа"
                      >
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>
                      </button>
                    </div>
                  )}
                  {isEditingMessage && editingMessage && (
                    <div className="tg-composer-strip tg-composer-edit-strip">
                      <svg className="tg-composer-strip-icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                      </svg>
                      <div className="tg-composer-strip-body">
                        <div className="tg-composer-strip-label">Редактирование</div>
                        <div className="tg-composer-strip-text">{editingMessage.content}</div>
                      </div>
                      <button
                        type="button"
                        className="tg-composer-strip-close"
                        onClick={handleCancelMessageEdit}
                        aria-label="Отмена редактирования"
                      >
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>
                      </button>
                    </div>
                  )}
                  {pendingFiles.length > 0 && (
                    <div className="tg-file-previews">
                      {pendingFiles.map((file, index) => {
                        const previewUrl = filePreviewUrls[index] ?? null;
                        return (
                          <div key={`${file.name}-${index}`} className="tg-file-preview">
                            {previewUrl ? (
                              <img src={previewUrl} alt={file.name} className="tg-file-preview-thumb" />
                            ) : (
                              <div className="tg-file-preview-icon">
                                <svg viewBox="0 0 24 24" aria-hidden="true">
                                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                                  <polyline points="14 2 14 8 20 8"/>
                                </svg>
                              </div>
                            )}
                            <span className="tg-file-preview-name">{file.name}</span>
                            <button
                              type="button"
                              className="tg-file-preview-remove"
                              onClick={() => setPendingFiles((prev) => prev.filter((_, i) => i !== index))}
                              aria-label={`Удалить ${file.name}`}
                            >
                              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  )}
                  {emojiPickerOpen && (
                    <div className="tg-emoji-picker">
                      <div className="tg-emoji-categories">
                        {EMOJI_CATEGORIES.map((cat, i) => (
                          <button
                            key={i}
                            type="button"
                            className={`tg-emoji-cat-btn${emojiCategory === i ? " active" : ""}`}
                            onClick={() => setEmojiCategory(i)}
                            title={cat.name}
                          >
                            {cat.icon}
                          </button>
                        ))}
                      </div>
                      <div className="tg-emoji-grid">
                        {EMOJI_CATEGORIES[emojiCategory].emojis.map((emoji) => (
                          <button
                            key={emoji}
                            type="button"
                            className="tg-emoji-btn"
                            onClick={() => {
                              setComposerValue((v) => v + emoji);
                              requestAnimationFrame(() => {
                                composerTextareaRef.current?.focus();
                                resizeTextarea();
                              });
                            }}
                          >
                            {emoji}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                  <div className="tg-composer-shell">
                    <button
                      type="button"
                      className="tg-composer-attach"
                      style={isEditingMessage ? { visibility: "hidden" } : undefined}
                      onClick={() => fileInputRef.current?.click()}
                      aria-label="Прикрепить файл"
                      title="Прикрепить файл"
                      disabled={isEditingMessage}
                      tabIndex={isEditingMessage ? -1 : undefined}
                    >
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
                      </svg>
                    </button>
                    <textarea
                      ref={composerTextareaRef}
                      rows={1}
                      placeholder={isEditingMessage ? "Изменить сообщение..." : "Написать сообщение..."}
                      value={composerValue}
                      onChange={handleComposerChange}
                      onPaste={handleComposerPaste}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" && !event.shiftKey) {
                          event.preventDefault();
                          event.currentTarget.form?.requestSubmit();
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="tg-composer-emoji-btn"
                      onClick={() => setEmojiPickerOpen((v) => !v)}
                      aria-label="Эмодзи"
                      title="Эмодзи"
                    >
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <circle cx="12" cy="12" r="10"/>
                        <path d="M8 13s1.5 2 4 2 4-2 4-2"/>
                        <line x1="9" y1="9" x2="9.01" y2="9"/>
                        <line x1="15" y1="9" x2="15.01" y2="9"/>
                      </svg>
                    </button>
                    <button
                      type="submit"
                      className="tg-composer-send"
                      disabled={!canSubmitMessage}
                      aria-label={isEditingMessage ? "Сохранить изменения" : "Отправить сообщение"}
                      title={canSubmitMessage ? (isEditingMessage ? "Сохранить" : "Отправить") : "Введите сообщение"}
                    >
                      {isEditingMessage ? (
                        <svg className="tg-send-icon" viewBox="0 0 24 24" aria-hidden="true">
                          <polyline points="20 6 9 17 4 12"/>
                        </svg>
                      ) : (
                        <svg className="tg-send-icon" viewBox="0 0 24 24" aria-hidden="true">
                          <line x1="22" y1="2" x2="11" y2="13"/>
                          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                        </svg>
                      )}
                    </button>
                  </div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    multiple
                    accept="image/*,application/pdf,.doc,.docx,.txt,.zip,.rar,.7z,.mp4,.mp3,.wav"
                    style={{ display: "none" }}
                    onChange={(event) => {
                      const files = Array.from(event.target.files ?? []);
                      if (files.length === 0) { event.target.value = ""; return; }
                      setPendingFiles((prev) => {
                        const currentImages = prev.filter(f => f.type.startsWith("image/")).length;
                        const newImages = files.filter(f => f.type.startsWith("image/"));
                        const newOther = files.filter(f => !f.type.startsWith("image/"));
                        const canAdd = Math.max(0, 10 - currentImages);
                        if (newImages.length > canAdd) setNotice(`Максимум 10 фото в одном сообщении`);
                        return [...prev, ...newImages.slice(0, canAdd), ...newOther];
                      });
                      event.target.value = "";
                    }}
                  />
                </form>

                {/* Group info panel */}
                {groupInfoOpen && activeChatId !== null && (
                  <div className="tg-group-info-panel">
                    <div className="tg-group-info-head">
                      <AvatarImage
                        name={activeChat?.chatName ?? "Г"}
                        seed={activeChatId}
                        avatarUrl={activeChat?.avatarUrl ?? undefined}
                        size="sm"
                        fetchMedia={api.fetchMediaBlob}
                      />
                      <h3>{activeChat?.chatName ?? "Группа"}</h3>
                      <button
                        type="button"
                        className="tg-group-info-close"
                        onClick={() => setGroupInfoOpen(false)}
                        aria-label="Закрыть"
                      >
                        ✕
                      </button>
                    </div>
                    <div className="tg-group-info-body">
                      {(() => {
                        const myRole = activeChatParticipants.find((p) => p.userId === profile.userId)?.role;
                        const canManage = myRole === "OWNER" || myRole === "ADMIN";
                        return (
                          <>
                            {canManage && (
                              <div style={{ display: "flex", gap: "0.4rem", padding: "0.2rem 0.36rem", flexWrap: "wrap" }}>
                                <input
                                  ref={groupAvatarInputRef}
                                  type="file"
                                  accept="image/*"
                                  style={{ display: "none" }}
                                  onChange={(e) => {
                                    const file = e.target.files?.[0];
                                    if (file) void handleGroupAvatarUpload(file);
                                    e.target.value = "";
                                  }}
                                />
                                <button
                                  type="button"
                                  className="secondary"
                                  style={{ fontSize: "0.8rem", padding: "0.38rem 0.7rem", borderRadius: "9px" }}
                                  onClick={() => {
                                    setChatRenameDraft(activeChat?.chatName ?? "");
                                    setChatRenameOpen(true);
                                    setGroupInfoOpen(false);
                                  }}
                                >
                                  Переименовать
                                </button>
                                <button
                                  type="button"
                                  className="secondary"
                                  style={{ fontSize: "0.8rem", padding: "0.38rem 0.7rem", borderRadius: "9px" }}
                                  onClick={() => {
                                    setAddMemberTag("");
                                    setAddMemberResults([]);
                                    setAddMemberOpen(true);
                                  }}
                                >
                                  + Участник
                                </button>
                                <button
                                  type="button"
                                  className="secondary"
                                  style={{ fontSize: "0.8rem", padding: "0.38rem 0.7rem", borderRadius: "9px" }}
                                  disabled={groupAvatarUploading}
                                  onClick={() => groupAvatarInputRef.current?.click()}
                                >
                                  {groupAvatarUploading ? "..." : (activeChat?.avatarUrl ? "Сменить фото" : "Фото группы")}
                                </button>
                                {activeChat?.avatarUrl && (
                                  <button
                                    type="button"
                                    className="ghost"
                                    style={{ fontSize: "0.8rem", padding: "0.38rem 0.7rem", borderRadius: "9px" }}
                                    disabled={groupAvatarUploading}
                                    onClick={() => void handleGroupAvatarRemove()}
                                  >
                                    Удалить фото
                                  </button>
                                )}
                              </div>
                            )}
                            <div className="tg-group-info-section">
                              Участники {activeChatParticipantsLoading ? "..." : `(${activeChatParticipants.length})`}
                            </div>
                            {activeChatParticipants.map((p) => {
                              const isMe = p.userId === profile.userId;
                              const name = getUserDisplayName(p.userId);
                              const memberAvatarUrl = p.userId === profile.userId ? profile.avatarUrl : avatarUrlByUserId[p.userId];
                              const memberOnline = presenceByUserId[p.userId] ?? false;
                              return (
                                <div key={p.userId} className="tg-member-row">
                                  <AvatarImage
                                    name={name}
                                    seed={p.userId}
                                    avatarUrl={memberAvatarUrl}
                                    size="xs"
                                    fetchMedia={api.fetchMediaBlob}
                                  />
                                  <div className="tg-member-info">
                                    <div className="tg-member-name">{name}{isMe ? " (вы)" : ""}</div>
                                    <div className={`tg-member-status${memberOnline ? " online" : ""}`}>
                                      {memberOnline ? "в сети" : "не в сети"}
                                    </div>
                                  </div>
                                  <span className={`tg-role-badge ${p.role.toLowerCase()}`}>
                                    {p.role === "OWNER" ? "владелец" : p.role === "ADMIN" ? "админ" : "участник"}
                                  </span>
                                  {!isMe && myRole === "OWNER" && p.role !== "OWNER" && (
                                    <div className="tg-member-actions">
                                      {p.role === "MEMBER" && (
                                        <button
                                          type="button"
                                          className="tg-member-action-btn"
                                          title="Назначить админом"
                                          onClick={() => void handleUpdateMemberRole(p.userId, "ADMIN")}
                                        >
                                          ↑
                                        </button>
                                      )}
                                      {p.role === "ADMIN" && (
                                        <button
                                          type="button"
                                          className="tg-member-action-btn"
                                          title="Разжаловать"
                                          onClick={() => void handleUpdateMemberRole(p.userId, "MEMBER")}
                                        >
                                          ↓
                                        </button>
                                      )}
                                    </div>
                                  )}
                                </div>
                              );
                            })}
                          </>
                        );
                      })()}
                    </div>
                    <div className="tg-group-info-footer">
                      <button
                        type="button"
                        className="ghost tg-danger"
                        style={{ fontSize: "0.86rem", width: "100%" }}
                        onClick={() => void handleLeaveChat(activeChatId)}
                        disabled={leaveChatBusy}
                      >
                        {leaveChatBusy ? "..." : "Покинуть группу"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="tg-empty-state grow">
                <h3>Выберите чат</h3>
                <p>Выберите чат слева, чтобы загрузить историю и продолжить переписку.</p>
              </div>
            )}
          </section>
        ) : isSearchRoute ? (
          <section className="tg-search-pane">
            <header className="tg-search-head">
              <div className="tg-head-main">
                <button
                  type="button"
                  className="tg-back-btn"
                  onClick={() => navigate("/")}
                  aria-label="Назад"
                  title="Назад"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M15 5L8 12L15 19" />
                  </svg>
                </button>
                <div>
                  <p className="tg-label">Поиск</p>
                  <h2>Новый чат</h2>
                  <p className="tg-meta">Введите тег пользователя, чтобы начать диалог.</p>
                </div>
              </div>
            </header>

            <div className="tg-search-pane-body">
              <div className="tg-search-box tg-search-box-main" role="search">
                <span className="tg-search-icon" aria-hidden="true">
                  <svg viewBox="0 0 16 16">
                    <circle cx="7" cy="7" r="4.7" fill="none" stroke="currentColor" strokeWidth="1.5" />
                    <path d="M10.4 10.4L14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                  </svg>
                </span>
                <input
                  ref={searchInputRef}
                  type="text"
                  minLength={3}
                  maxLength={15}
                  value={newChatTagInput}
                  onChange={(event) => setNewChatTagInput(event.target.value)}
                  placeholder="Поиск по тегу: alex"
                />
                {chatSearchBusy && <span className="tg-search-spinner" aria-label="Поиск..." />}
              </div>

              <div className="tg-search-results tg-search-results-main">
                {showSearchIdleHint && (
                  <p className="tg-search-status">Начните вводить тег пользователя.</p>
                )}
                {searchTooShort && (
                  <p className="tg-search-status">Введите минимум 3 символа для поиска.</p>
                )}
                {chatSearchError && (
                  <p className="tg-search-status error">{chatSearchError}</p>
                )}
                {showSearchNoResults && (
                  <p className="tg-search-status">Ничего не найдено.</p>
                )}

                {chatSearchResults.map((candidate) => (
                  <button
                    key={candidate.userId}
                    type="button"
                    className="tg-search-row tg-search-row-action"
                    onClick={() => {
                      void handleCreateChatWithUser(candidate.userId);
                    }}
                    disabled={createChatBusy}
                  >
                    <div className="tg-search-row-info">
                      <strong>{candidate.name}</strong>
                      <span>@{candidate.tag}</span>
                    </div>
                    <span className="tg-search-row-cta" aria-hidden="true">›</span>
                  </button>
                ))}
              </div>
            </div>
          </section>
        ) : isProfileRoute ? (
          <section className="tg-settings-pane tg-profile-pane">
            <header className="tg-settings-head">
              <div className="tg-head-main">
                <button
                  type="button"
                  className="tg-back-btn"
                  onClick={() => navigate("/")}
                  aria-label="Назад"
                  title="Назад"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M15 5L8 12L15 19" />
                  </svg>
                </button>
                <div>
                  <p className="tg-label">Профиль</p>
                  <h2>{profileViewData?.name ?? "Профиль пользователя"}</h2>
                </div>
              </div>
            </header>

            {profileViewLoading ? (
              <div className="tg-empty-state grow">
                <h3>Загрузка профиля</h3>
                <p>Получаем данные пользователя...</p>
              </div>
            ) : profileViewError ? (
              <div className="tg-empty-state grow">
                <h3>Не удалось загрузить профиль</h3>
                <p>{profileViewError}</p>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setProfileViewReloadTick((previous) => previous + 1);
                  }}
                >
                  Повторить
                </button>
              </div>
            ) : profileViewData ? (
              <>
                <div className="tg-profile-hero">
                  <AvatarImage
                    name={profileViewData.name}
                    seed={profileViewData.userId}
                    avatarUrl={avatarUrlByUserId[profileViewData.userId] ?? profileViewData.avatarUrl}
                    fetchMedia={api.fetchMediaBlob}
                  />
                  <div className="tg-profile-hero-info">
                    <h3 className="tg-profile-hero-name">{profileViewData.name}</h3>
                    <p className="tg-profile-hero-tag">@{profileViewData.tag}</p>
                    {profileViewData.description && profileViewData.description.trim() && (
                      <p className="tg-profile-hero-desc">{profileViewData.description}</p>
                    )}
                  </div>
                </div>
                <section className="tg-card tg-settings-card">
                  <h3>Основное</h3>
                  <div className="tg-info-row">
                    <span className="tg-info-label">Имя</span>
                    <span className="tg-info-value">{profileViewData.name}</span>
                  </div>
                  <div className="tg-info-row">
                    <span className="tg-info-label">Тег</span>
                    <span className="tg-info-value">@{profileViewData.tag}</span>
                  </div>
                  {profileViewData.description && profileViewData.description.trim() && (
                    <div className="tg-info-row">
                      <span className="tg-info-label">О себе</span>
                      <span className="tg-info-value">{profileViewData.description}</span>
                    </div>
                  )}
                  {profileViewData.userId === profile.userId && (
                    <div style={{ marginTop: "0.6rem" }}>
                      <button type="button" className="secondary" onClick={() => navigate("/settings/profile")}>
                        Изменить профиль
                      </button>
                    </div>
                  )}
                </section>
              </>
            ) : (
              <div className="tg-empty-state grow">
                <h3>Профиль недоступен</h3>
                <p>Пользователь не найден.</p>
              </div>
            )}
          </section>
        ) : isSettingsHomeRoute ? (
          <section className="tg-settings-pane tg-settings-profile-view">
            <header className="tg-settings-head">
              <div className="tg-head-main">
                <button
                  type="button"
                  className="tg-back-btn"
                  onClick={() => navigate("/")}
                  aria-label="Назад"
                  title="Назад"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M15 5L8 12L15 19" />
                  </svg>
                </button>
                <div>
                  <p className="tg-label">Аккаунт</p>
                  <h2>Настройки</h2>
                  <p className="tg-meta">Выберите категорию</p>
                </div>
              </div>
            </header>

            <div className="tg-settings-categories">
              <button
                type="button"
                className="tg-card tg-settings-category"
                onClick={() => navigate("/settings/profile")}
              >
                <div className="tg-settings-category-main">
                  <h3>Профиль</h3>
                  <p className="tg-meta">Имя, тег и описание</p>
                </div>
                <span className="tg-settings-category-arrow" aria-hidden="true">›</span>
              </button>

              <button
                type="button"
                className="tg-card tg-settings-category"
                onClick={() => navigate("/settings/appearance")}
              >
                <div className="tg-settings-category-main">
                  <h3>Оформление</h3>
                  <p className="tg-meta">Тема интерфейса</p>
                </div>
                <span className="tg-settings-category-arrow" aria-hidden="true">›</span>
              </button>
            </div>
          </section>
        ) : isSettingsProfileRoute ? (
          <section className="tg-settings-pane">
            <header className="tg-settings-head">
              <div className="tg-head-main">
                <button
                  type="button"
                  className="tg-back-btn"
                  onClick={() => navigate("/settings")}
                  aria-label="Назад"
                  title="Назад"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M15 5L8 12L15 19" />
                  </svg>
                </button>
                <div>
                  <p className="tg-label">Аккаунт</p>
                  <h2>Мой профиль</h2>
                </div>
              </div>
            </header>

            <div className="tg-profile-hero">
              <AvatarImage
                name={profile.name}
                seed={profile.userId}
                avatarUrl={profile.avatarUrl}
                fetchMedia={api.fetchMediaBlob}
              />
              <div className="tg-profile-hero-info">
                <h3 className="tg-profile-hero-name">{profile.name}</h3>
                <p className="tg-profile-hero-tag">@{profile.tag}</p>
                {profile.description && profile.description.trim() && (
                  <p className="tg-profile-hero-desc">{profile.description}</p>
                )}
              </div>
            </div>

            <section className="tg-card tg-settings-card">
              <h3>Основное</h3>
              <div className="tg-info-row">
                <span className="tg-info-label">Имя</span>
                <span className="tg-info-value">{profile.name}</span>
              </div>
              <div className="tg-info-row">
                <span className="tg-info-label">Тег</span>
                <span className="tg-info-value">@{profile.tag}</span>
              </div>
              {profile.description && profile.description.trim() && (
                <div className="tg-info-row">
                  <span className="tg-info-label">О себе</span>
                  <span className="tg-info-value">{profile.description}</span>
                </div>
              )}
              <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.6rem" }}>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => navigate("/settings/profile/edit")}
                >
                  Изменить профиль
                </button>
              </div>
            </section>

            <section className="tg-card tg-settings-card">
              <h3>Аккаунт</h3>
              <div className="tg-danger-zone" style={{ marginTop: 0, paddingTop: 0, borderTop: "none" }}>
                <button type="button" className="ghost" onClick={handleLogout}>
                  Выйти из аккаунта
                </button>
              </div>
            </section>
          </section>
        ) : isSettingsProfileEditRoute ? (
          <section className="tg-settings-pane">
            <header className="tg-settings-head">
              <div className="tg-head-main">
                <button
                  type="button"
                  className="tg-back-btn"
                  onClick={() => navigate("/settings/profile")}
                  aria-label="Назад"
                  title="Назад"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M15 5L8 12L15 19" />
                  </svg>
                </button>
                <div>
                  <p className="tg-label">Категория</p>
                  <h2>Изменение профиля</h2>
                  <p className="tg-meta">Обновите публичные данные профиля</p>
                </div>
              </div>
            </header>

            <section className="tg-card tg-settings-card">
              <div className="tg-avatar-edit-section">
                <div
                  className={`tg-avatar-edit-wrap${avatarUploading ? " loading" : ""}`}
                  onClick={() => !avatarUploading && avatarFileInputRef.current?.click()}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={(e) => {
                    e.preventDefault();
                    const file = e.dataTransfer.files[0];
                    if (file && file.type.startsWith("image/")) void handleAvatarUpload(file);
                  }}
                  role="button"
                  tabIndex={0}
                  aria-label="Изменить аватар"
                  onKeyDown={(e) => { if (e.key === "Enter") avatarFileInputRef.current?.click(); }}
                  title="Нажмите или перетащите фото"
                >
                  <AvatarImage
                    name={profile.name}
                    seed={profile.userId}
                    avatarUrl={profile.avatarUrl}
                    fetchMedia={api.fetchMediaBlob}
                  />
                  <div className="tg-avatar-edit-overlay">
                    {avatarUploading ? (
                      <span className="tg-image-upload-spinner" />
                    ) : (
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
                        <circle cx="12" cy="13" r="4"/>
                      </svg>
                    )}
                  </div>
                </div>
                <div className="tg-avatar-edit-actions">
                  <button
                    type="button"
                    className="secondary"
                    style={{ fontSize: "0.82rem", padding: "0.36rem 0.8rem" }}
                    onClick={() => avatarFileInputRef.current?.click()}
                    disabled={avatarUploading}
                  >
                    {profile.avatarUrl ? "Изменить фото" : "Загрузить фото"}
                  </button>
                  {profile.avatarUrl && (
                    <button
                      type="button"
                      className="ghost"
                      style={{ fontSize: "0.82rem", padding: "0.36rem 0.8rem" }}
                      onClick={handleAvatarRemove}
                      disabled={avatarUploading}
                    >
                      Удалить
                    </button>
                  )}
                </div>
                <input
                  ref={avatarFileInputRef}
                  type="file"
                  accept="image/*"
                  style={{ display: "none" }}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) void handleAvatarUpload(file);
                    e.target.value = "";
                  }}
                />
              </div>

              <form className="tg-form" onSubmit={handleProfileSave}>
                <label>
                  Имя
                  <input
                    type="text"
                    minLength={4}
                    maxLength={50}
                    value={profileNameDraft}
                    onChange={(event) => setProfileNameDraft(event.target.value)}
                  />
                </label>
                <label>
                  Тег
                  <input
                    type="text"
                    minLength={3}
                    maxLength={15}
                    value={profileTagDraft}
                    onChange={(event) => setProfileTagDraft(event.target.value)}
                  />
                </label>
                <label>
                  О себе
                  <input
                    type="text"
                    maxLength={50}
                    value={profileDescriptionDraft}
                    onChange={(event) => setProfileDescriptionDraft(event.target.value)}
                  />
                </label>
                <button type="submit" className="secondary" disabled={profileSaving}>
                  {profileSaving ? "Сохранение..." : "Сохранить профиль"}
                </button>
              </form>
              <div className="tg-danger-zone">
                <p className="tg-meta">Удаление аккаунта удалит профиль и связанные чаты без возможности восстановления</p>
                <button
                  type="button"
                  className="ghost tg-danger"
                  onClick={handleProfileDelete}
                  disabled={profileDeleteBusy}
                >
                  {profileDeleteBusy ? "Удаление..." : "Удалить аккаунт"}
                </button>
              </div>
            </section>
          </section>
        ) : isSettingsAppearanceRoute ? (
          <section className="tg-settings-pane">
            <header className="tg-settings-head">
              <div className="tg-head-main">
                <button
                  type="button"
                  className="tg-back-btn"
                  onClick={() => navigate("/settings")}
                  aria-label="Назад"
                  title="Назад"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M15 5L8 12L15 19" />
                  </svg>
                </button>
                <div>
                  <p className="tg-label">Категория</p>
                  <h2>Оформление</h2>
                  <p className="tg-meta">Настройка внешнего вида</p>
                </div>
              </div>
            </header>

            <section className="tg-card tg-settings-card">
              <h3>Тема</h3>
              <div className="tg-theme-row">
                <span className="tg-meta">Тема: {themeMode === "dark" ? "Тёмная" : "Светлая"}</span>
                <button
                  type="button"
                  className={themeMode === "dark" ? "tg-theme-toggle dark" : "tg-theme-toggle"}
                  onClick={handleThemeToggle}
                  aria-label="Сменить тему"
                  title="Сменить тему"
                />
              </div>
            </section>
          </section>
        ) : null}
      </main>

      {messageMenu && messageMenuMessage && (
        <div
          ref={messageMenuRef}
          className="tg-message-menu"
          style={{ left: `${messageMenu.x}px`, top: `${messageMenu.y}px` }}
          onMouseDown={(event) => event.stopPropagation()}
        >
          <div className="tg-reaction-picker">
            {Object.entries(REACTION_EMOJIS).map(([type, emoji]) => {
              const serverId = normalizeServerId(messageMenuMessage.serverId);
              const isActive = serverId
                ? Boolean(
                  (reactionsByMessageId[serverId] ?? []).find((r) => r.reactionType === type)?.reactedByCurrentUser ||
                  (myReactionsByMessageId[serverId] ?? []).includes(type)
                )
                : false;
              return (
                <button
                  key={type}
                  type="button"
                  className={`tg-reaction-picker-btn${isActive ? " active" : ""}`}
                  title={type}
                  onClick={() => void handleToggleReaction(messageMenuMessage, type)}
                >
                  {emoji}
                </button>
              );
            })}
          </div>
          <div className="tg-message-menu-divider" />
          <button
            type="button"
            className="tg-message-menu-item"
            onClick={() => handleSetReplyTo(messageMenuMessage)}
          >
            Ответить
          </button>
          <button
            type="button"
            className="tg-message-menu-item"
            onClick={() => {
              setMessageMenu(null);
              void handleCopyMessage(messageMenuMessage);
            }}
          >
            Копировать
          </button>
          {normalizeServerId(messageMenuMessage.serverId) && (
            <button
              type="button"
              className="tg-message-menu-item"
              onClick={() => handleForwardMessage(messageMenuMessage)}
            >
              Переслать
            </button>
          )}
          {messageMenuCanEdit && (
            <button
              type="button"
              className="tg-message-menu-item"
              onClick={() => {
                setMessageMenu(null);
                handleStartEditingMessage(messageMenuMessage);
              }}
            >
              Редактировать
            </button>
          )}
          {messageMenuCanRetry && (
            <button
              type="button"
              className="tg-message-menu-item"
              onClick={() => {
                setMessageMenu(null);
                void handleRetryMessage(messageMenuMessage);
              }}
            >
              Повторить
            </button>
          )}
          {messageMenuCanDelete && (
            <button
              type="button"
              className="tg-message-menu-item danger"
              onClick={() => {
                setMessageMenu(null);
                void handleDeleteMessage(messageMenuMessage);
              }}
            >
              Удалить
            </button>
          )}
        </div>
      )}

      {chatMenu && chatMenuChat && (
        <div
          ref={chatMenuRef}
          className="tg-message-menu"
          style={{ left: `${chatMenu.x}px`, top: `${chatMenu.y}px` }}
          onMouseDown={(event) => event.stopPropagation()}
        >
          <button
            type="button"
            className="tg-message-menu-item"
            onClick={() => {
              setChatMenu(null);
              setMessageMenu(null);
              navigate("/");
              setActiveDraftPeerUserId(null);
              setActiveChatId(chatMenuChat.chatId);
              setGroupInfoOpen(false);
              setChatRenameOpen(false);
            }}
          >
            Открыть чат
          </button>
          {chatMenuChat.peerUserId && (
            <button
              type="button"
              className="tg-message-menu-item"
              onClick={() => {
                setChatMenu(null);
                setMessageMenu(null);
                setProfileViewUserId(chatMenuChat.peerUserId ?? null);
                navigate("/profile");
              }}
            >
              Открыть профиль
            </button>
          )}
          <button
            type="button"
            className="tg-message-menu-item"
            onClick={() => {
              setChatMenu(null);
              setMyRenameChatId(chatMenuChat.chatId);
              setMyRenameDraft(chatMenuChat.chatName ?? "");
              setMyRenameOpen(true);
            }}
          >
            Переименовать для себя
          </button>
          {chatTypeById[chatMenuChat.chatId] === "GROUP" && (
            <button
              type="button"
              className="tg-message-menu-item danger"
              onClick={() => {
                setChatMenu(null);
                void handleLeaveChat(chatMenuChat.chatId);
              }}
            >
              Покинуть группу
            </button>
          )}
          <button
            type="button"
            className="tg-message-menu-item danger"
            onClick={() => {
              setChatMenu(null);
              setMessageMenu(null);
              void deleteChatById(chatMenuChat.chatId, chatMenuChat.peerUserId ?? null, chatMenuChat.chatName);
            }}
          >
            Удалить чат
          </button>
        </div>
      )}

      {/* ── Personal rename modal ────────────────── */}
      {myRenameOpen && myRenameChatId !== null && (
        <div className="tg-modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) setMyRenameOpen(false); }}>
          <div className="tg-modal">
            <div className="tg-modal-head">
              <h2>Переименовать для себя</h2>
              <button type="button" className="tg-modal-close" onClick={() => setMyRenameOpen(false)}>✕</button>
            </div>
            <div style={{ padding: "0 1rem 1rem" }}>
              <p className="tg-meta" style={{ marginBottom: "0.8rem" }}>Только вы будете видеть это название</p>
              <form className="tg-form" onSubmit={(e) => { e.preventDefault(); void handleMyRename(); }}>
                <input
                  type="text"
                  maxLength={63}
                  placeholder="Введите название..."
                  value={myRenameDraft}
                  onChange={(e) => setMyRenameDraft(e.target.value)}
                  autoFocus
                />
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button type="submit" className="primary" disabled={myRenameBusy} style={{ flex: 1 }}>
                    {myRenameBusy ? "..." : "Сохранить"}
                  </button>
                  {(() => {
                    const chat = sortedChats.find((c) => c.chatId === myRenameChatId);
                    return chat?.chatName ? (
                      <button
                        type="button"
                        className="ghost"
                        disabled={myRenameBusy}
                        onClick={() => { setMyRenameDraft(""); void handleMyRename(); }}
                      >
                        Сбросить
                      </button>
                    ) : null;
                  })()}
                  <button type="button" className="secondary" onClick={() => setMyRenameOpen(false)}>Отмена</button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* ── Forward Message Modal ─────────────────── */}
      {forwardModalMessage && (
        <div className="tg-modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) setForwardModalMessage(null); }}>
          <div className="tg-modal">
            <div className="tg-modal-head">
              <h2>Переслать сообщение</h2>
              <button type="button" className="tg-modal-close" onClick={() => setForwardModalMessage(null)}>✕</button>
            </div>
            <div className="tg-forward-preview">
              <p className="tg-meta">{forwardModalMessage.content || "📎 Медиафайл"}</p>
            </div>
            <div className="tg-forward-chat-list">
              {sortedChats.map((chat) => {
                const knownType = chatTypeById[chat.chatId];
                const displayName = chat.chatName
                  || (knownType === "PRIVATE" && chat.peerUserId ? getUserDisplayName(chat.peerUserId) : "Чат");
                const fwdSeed = chat.peerUserId ?? chat.chatId;
                const fwdAvatarUrl = chat.peerUserId != null
                  ? (avatarUrlByUserId[chat.peerUserId] ?? undefined)
                  : (chat.avatarUrl ?? undefined);
                return (
                  <button
                    key={chat.chatId}
                    type="button"
                    className="tg-forward-chat-row"
                    onClick={() => void handleConfirmForward(chat.chatId)}
                  >
                    <AvatarImage name={displayName} seed={fwdSeed} avatarUrl={fwdAvatarUrl} size="sm" fetchMedia={api.fetchMediaBlob} />
                    <span className="tg-forward-chat-name">{displayName}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* ── Create Group Modal ─────────────────────── */}
      {createGroupOpen && (
        <div className="tg-modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) setCreateGroupOpen(false); }}>
          <div className="tg-modal">
            <div className="tg-modal-head">
              <h2>Новая группа</h2>
              <button type="button" className="tg-modal-close" onClick={() => setCreateGroupOpen(false)}>✕</button>
            </div>
            <div className="tg-modal-body">
              <label style={{ display: "grid", gap: "0.3rem", fontSize: "0.84rem", color: "var(--text-soft)", fontWeight: 600 }}>
                Название группы
                <input
                  type="text"
                  maxLength={63}
                  placeholder="Например: Рабочая группа"
                  value={groupNameDraft}
                  onChange={(e) => setGroupNameDraft(e.target.value)}
                  autoFocus
                />
              </label>
              <label style={{ display: "grid", gap: "0.3rem", fontSize: "0.84rem", color: "var(--text-soft)", fontWeight: 600 }}>
                Добавить участников по тегу
                <div className="tg-search-box">
                  <span className="tg-search-icon" aria-hidden="true">
                    <svg viewBox="0 0 16 16"><circle cx="7" cy="7" r="4.7" fill="none" stroke="currentColor" strokeWidth="1.5" /><path d="M10.4 10.4L14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
                  </span>
                  <input
                    type="text"
                    minLength={3}
                    maxLength={15}
                    value={groupSearchTag}
                    onChange={(e) => setGroupSearchTag(e.target.value)}
                    placeholder="Введите тег..."
                  />
                  {groupSearchBusy && <span className="tg-search-spinner" aria-label="Поиск..." />}
                </div>
              </label>
              {groupSelectedMembers.length > 0 && (
                <div className="tg-selected-members">
                  {groupSelectedMembers.map((m) => (
                    <span key={m.userId} className="tg-member-chip">
                      {m.name}
                      <button
                        type="button"
                        onClick={() => setGroupSelectedMembers((prev) => prev.filter((x) => x.userId !== m.userId))}
                        aria-label={`Убрать ${m.name}`}
                      >
                        ✕
                      </button>
                    </span>
                  ))}
                </div>
              )}
              {groupSearchResults.length > 0 && (
                <div className="tg-search-results">
                  {groupSearchResults.map((u) => (
                    <button
                      key={u.userId}
                      type="button"
                      className="tg-search-row tg-search-row-action"
                      onClick={() => {
                        setGroupSelectedMembers((prev) => [...prev, u]);
                        setGroupSearchTag("");
                        setGroupSearchResults([]);
                      }}
                    >
                      <div className="tg-search-row-info">
                        <strong>{u.name}</strong>
                        <span>@{u.tag}</span>
                      </div>
                      <span className="tg-search-row-cta" aria-hidden="true">+</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div className="tg-modal-footer">
              <button type="button" className="ghost" onClick={() => setCreateGroupOpen(false)}>
                Отмена
              </button>
              <button
                type="button"
                className="primary"
                disabled={groupCreateBusy || !groupNameDraft.trim() || groupSelectedMembers.length === 0}
                onClick={() => void handleCreateGroup()}
              >
                {groupCreateBusy ? "Создание..." : "Создать группу"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Add Member Modal ────────────────────────── */}
      {addMemberOpen && (
        <div className="tg-modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) setAddMemberOpen(false); }}>
          <div className="tg-modal">
            <div className="tg-modal-head">
              <h2>Добавить участника</h2>
              <button type="button" className="tg-modal-close" onClick={() => setAddMemberOpen(false)}>✕</button>
            </div>
            <div className="tg-modal-body">
              <div className="tg-search-box">
                <span className="tg-search-icon" aria-hidden="true">
                  <svg viewBox="0 0 16 16"><circle cx="7" cy="7" r="4.7" fill="none" stroke="currentColor" strokeWidth="1.5" /><path d="M10.4 10.4L14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
                </span>
                <input
                  type="text"
                  minLength={3}
                  maxLength={15}
                  value={addMemberTag}
                  onChange={(e) => setAddMemberTag(e.target.value)}
                  placeholder="Введите тег..."
                  autoFocus
                />
                {addMemberSearchBusy && <span className="tg-search-spinner" aria-label="Поиск..." />}
              </div>
              {addMemberResults.length > 0 && (
                <div className="tg-search-results">
                  {addMemberResults.map((u) => (
                    <button
                      key={u.userId}
                      type="button"
                      className="tg-search-row tg-search-row-action"
                      onClick={() => void handleAddMemberSubmit(u.userId)}
                      disabled={addMemberBusy}
                    >
                      <div className="tg-search-row-info">
                        <strong>{u.name}</strong>
                        <span>@{u.tag}</span>
                      </div>
                      <span className="tg-search-row-cta" aria-hidden="true">+</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div className="tg-modal-footer">
              <button type="button" className="ghost" onClick={() => setAddMemberOpen(false)}>
                Закрыть
              </button>
            </div>
          </div>
        </div>
      )}

      {notice && <div className="tg-toast">{notice}</div>}
    </div>
  );
}

export default App;
