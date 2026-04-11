// Mirrors backend DTOs under com.koval.trainingplannerbackend.chat.

export type ChatRoomScope =
  | 'CLUB'
  | 'GROUP'
  | 'OBJECTIVE'
  | 'RECURRING_SESSION'
  | 'SINGLE_SESSION'
  | 'DIRECT';

export type ChatMessageType = 'TEXT' | 'SYSTEM';

export interface ChatRoomSummary {
  id: string;
  scope: ChatRoomScope;
  clubId: string | null;
  scopeRefId: string | null;
  title: string;
  joinable: boolean;
  muted: boolean;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  lastMessageSenderId: string | null;
  unreadCount: number;
  /** Non-null only for DM rooms — the peer's user id. */
  otherUserId: string | null;
}

export interface ChatRoomDetail {
  id: string;
  scope: ChatRoomScope;
  clubId: string | null;
  scopeRefId: string | null;
  title: string;
  joinable: boolean;
  archived: boolean;
  createdAt: string | null;
  createdBy: string | null;
  lastMessageAt: string | null;
  currentUserIsMember: boolean;
  currentUserMuted: boolean;
  currentUserLastReadAt: string | null;
  otherUserId: string | null;
}

export interface ChatMessage {
  id: string;
  roomId: string;
  senderId: string;
  senderDisplayName: string;
  senderProfilePicture: string | null;
  content: string;
  createdAt: string;
  editedAt: string | null;
  deleted: boolean;
  type: ChatMessageType;
}

export interface PostMessageRequest {
  content: string;
  clientNonce?: string;
}

export interface CreateDirectRoomRequest {
  otherUserId: string;
}
