import {
  ClubFeedEventResponse,
  ClubFeedResponse,
  FeedCommentEntry,
  ReactionEmoji,
  ReactionUpdatePayload,
} from '../models/club.model';

/** Maps an updater over both `pinned` and `items` of a feed response. */
export function mapFeedEvents(
  feed: ClubFeedResponse,
  updater: (event: ClubFeedEventResponse) => ClubFeedEventResponse,
): ClubFeedResponse {
  return {
    ...feed,
    pinned: feed.pinned.map(updater),
    items: feed.items.map(updater),
  };
}

/** Applies a per-event mutation when `event.id === targetId`. */
export function updateEvent(
  targetId: string,
  mutate: (event: ClubFeedEventResponse) => ClubFeedEventResponse,
): (event: ClubFeedEventResponse) => ClubFeedEventResponse {
  return (event) => (event.id === targetId ? mutate(event) : event);
}

/** Appends a comment to an event if it isn't already present. */
export function appendCommentIfMissing(
  event: ClubFeedEventResponse,
  comment: FeedCommentEntry,
): ClubFeedEventResponse {
  const comments = [...(event.comments ?? [])];
  if (!comments.find((c) => c.id === comment.id)) {
    comments.push(comment);
  }
  return {...event, comments};
}

/** Replaces a comment in an event by id. */
export function replaceCommentById(
  event: ClubFeedEventResponse,
  comment: FeedCommentEntry,
): ClubFeedEventResponse {
  const comments = (event.comments ?? []).map((c) => (c.id === comment.id ? comment : c));
  return {...event, comments};
}

/** Removes a comment from an event by id. */
export function removeCommentById(
  event: ClubFeedEventResponse,
  commentId: string,
): ClubFeedEventResponse {
  return {...event, comments: (event.comments ?? []).filter((c) => c.id !== commentId)};
}

/** Applies a reaction add/remove to a reactions map. */
export function applyReactionToMap(
  reactions: Record<string, string[]> | undefined,
  emoji: ReactionEmoji,
  actorUserId: string,
  added: boolean,
): Record<string, string[]> {
  const next = {...(reactions ?? {})};
  const users = new Set(next[emoji] ?? []);
  if (added) users.add(actorUserId);
  else users.delete(actorUserId);
  if (users.size === 0) delete next[emoji];
  else next[emoji] = Array.from(users);
  return next;
}

/** Applies a reaction update payload to either an event or one of its comments. */
export function applyReactionUpdateToEvent(
  event: ClubFeedEventResponse,
  payload: ReactionUpdatePayload,
): ClubFeedEventResponse {
  if (!payload.commentId) {
    return {
      ...event,
      reactions: applyReactionToMap(
        event.reactions,
        payload.emoji,
        payload.actorUserId,
        payload.added,
      ),
    };
  }
  const comments = (event.comments ?? []).map((c) => {
    if (c.id !== payload.commentId) return c;
    return {
      ...c,
      reactions: applyReactionToMap(c.reactions, payload.emoji, payload.actorUserId, payload.added),
    };
  });
  return {...event, comments};
}
