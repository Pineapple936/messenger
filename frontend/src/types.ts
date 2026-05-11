export type TokenPair = {
  token: string;
  refreshToken: string;
};

export type UserProfile = {
  userId: number;
  tag: string;
  name: string;
  description: string | null;
  avatarUrl?: string | null;
};

export type ChatInfo = {
  chatId: number;
  chatName: string;
  lastMessageAt: string | null;
  lastMessagePreview?: string | null;
  lastMessageUserId?: number | null;
  lastMessageHasMedia?: boolean | null;
  avatarUrl?: string | null;
};

export type KnownChat = {
  chatId: number;
  chatName: string;
  peerUserId: number | null;
  updatedAt: string;
  lastMessagePreview?: string | null;
  lastMessageUserId?: number | null;
  lastMessageHasMedia?: boolean | null;
  avatarUrl?: string | null;
};

export type RepliedMessageInfo = {
  id: string;
  userId: number;
  content: string;
  sendAt: string | null;
};

export type MessageHistoryItem = {
  id?: string | number | null;
  chatId: number;
  userId: number;
  content: string;
  readStatus?: boolean | null;
  editStatus?: boolean | null;
  sendAt?: string | null;
  createdAt?: string | null;
  repliedMessage?: RepliedMessageInfo | null;
  photoLinks?: string[] | null;
};

export type MessageSlice = {
  content: MessageHistoryItem[];
  number: number;
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

export type ChatSlice = {
  content: ChatInfo[];
  number: number;
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

export type ChatMessage = {
  id: string;
  chatId: number;
  userId: number;
  content: string;
  createdAt: string | null;
  serverId?: string | number;
  edited?: boolean;
  delivery: "pending" | "sent" | "read" | "failed";
  origin: "local" | "remote";
  repliedMessage?: RepliedMessageInfo | null;
  photoLinks?: string[] | null;
};

export type LoginPayload = {
  email: string;
  password: string;
};

export type RegisterPayload = {
  email: string;
  password: string;
  name: string;
  tag: string;
  description: string | null;
};

export type EditProfilePayload = {
  name?: string | null;
  tag?: string | null;
  description?: string | null;
};

export type ChatParticipantDto = {
  userId: number;
  role: "OWNER" | "ADMIN" | "MEMBER";
  customChatName: string | null;
};

export type Reaction = {
  reactionType: string;
  count: number;
  reactedByCurrentUser?: boolean;
};

export const REACTION_EMOJIS: Record<string, string> = {
  LIKE: "👍",
  LOVE: "❤️",
  WOW: "😮",
  SAD: "😢",
  ANGRY: "😡",
  LAUGH: "😂",
  THINKING: "🤔",
  HANDSHAKE: "👋",
  WHITE_HEART: "🤍",
  PURPLE_HEART: "💜",
  YELLOW_HEART: "💛",
  GREEN_HEART: "💚",
  BROKEN_HEART: "💔",
  MIND_BLOWN: "🤯",
  PRAY: "🙏",
  PARTY: "🥳",
  HEART_EYES: "😍",
  CRYING: "😭",
  SCREAM: "😱",
  CLAP: "👏",
  COOL: "😎"
};
