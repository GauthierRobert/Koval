package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.CommentUpdatePayload;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Threaded comments on feed events: top-level comments and one-level replies,
 * with @mentions, edit/delete authorization, and SSE broadcasts.
 */
@Service
public class ClubFeedCommentService {

    private static final int NOTIFICATION_PREVIEW_CHARS = 100;

    private final ClubFeedEventRepository feedEventRepository;
    private final ClubAuthorizationService authorizationService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final MentionResolver mentionResolver;

    public ClubFeedCommentService(ClubFeedEventRepository feedEventRepository,
                                  ClubAuthorizationService authorizationService,
                                  UserService userService,
                                  NotificationService notificationService,
                                  ClubFeedSseBroadcaster broadcaster,
                                  MentionResolver mentionResolver) {
        this.feedEventRepository = feedEventRepository;
        this.authorizationService = authorizationService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
        this.mentionResolver = mentionResolver;
    }

    /**
     * Add a comment or reply with optional mentions. Pass {@code parentCommentId} to
     * make this a reply; replies must point at a top-level comment (one-level depth).
     */
    public ClubFeedEvent.CommentEntry addComment(String userId, String clubId, String feedEventId,
                                                 String content, String parentCommentId,
                                                 List<String> mentionUserIds) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = requireEvent(clubId, feedEventId);

        ClubFeedEvent.CommentEntry parent = null;
        if (parentCommentId != null) {
            parent = event.getComments().stream()
                    .filter(c -> c.id().equals(parentCommentId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found"));
            if (parent.parentCommentId() != null) {
                throw new IllegalStateException("Replies are limited to one level deep");
            }
        }

        User author = userService.findById(userId).orElseThrow();
        String commentId = UUID.randomUUID().toString();

        List<ClubFeedEvent.MentionRef> mentions = mentionResolver.resolve(
                clubId, mentionUserIds,
                parentCommentId == null ? MentionResolver.CONTEXT_COMMENT : MentionResolver.CONTEXT_REPLY,
                commentId);

        ClubFeedEvent.CommentEntry comment = new ClubFeedEvent.CommentEntry(
                commentId,
                userId,
                author.getDisplayName(),
                author.getProfilePicture(),
                content,
                LocalDateTime.now(),
                null,
                parentCommentId,
                new HashMap<>(),
                mentions);

        event.getComments().add(comment);
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        String broadcastEvent = parentCommentId == null ? "comment_update" : "comment_reply_added";
        broadcaster.broadcast(clubId, broadcastEvent, new CommentUpdatePayload(feedEventId, comment));

        notifyParentAndMentions(clubId, feedEventId, commentId, userId, author, content, parent, mentions);
        return comment;
    }

    /**
     * Edit an existing comment. Only the original author may edit.
     */
    public ClubFeedEvent.CommentEntry updateComment(String userId, String clubId, String feedEventId,
                                                    String commentId, String content) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = requireEvent(clubId, feedEventId);
        ClubFeedEvent.CommentEntry existing = findComment(event, commentId);

        if (!userId.equals(existing.userId())) {
            throw new IllegalStateException("Only the author can edit this comment");
        }

        LocalDateTime now = LocalDateTime.now();
        ClubFeedEvent.CommentEntry updated = new ClubFeedEvent.CommentEntry(
                existing.id(), existing.userId(), existing.displayName(), existing.profilePicture(),
                content, existing.createdAt(), now,
                existing.parentCommentId(),
                existing.reactions() != null ? existing.reactions() : new HashMap<>(),
                existing.mentions() != null ? existing.mentions() : List.of());

        int idx = event.getComments().indexOf(existing);
        event.getComments().set(idx, updated);
        event.setUpdatedAt(now);
        feedEventRepository.save(event);

        broadcaster.broadcast(clubId, "comment_edited", new CommentUpdatePayload(feedEventId, updated));
        return updated;
    }

    /**
     * Delete a comment. Author may delete; admins/coaches/owners may also delete
     * for moderation. Deleting a top-level comment cascades to its replies.
     */
    public void deleteComment(String userId, String clubId, String feedEventId, String commentId) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = requireEvent(clubId, feedEventId);
        ClubFeedEvent.CommentEntry existing = findComment(event, commentId);

        boolean isAuthor = userId.equals(existing.userId());
        boolean isAdmin = authorizationService.isAdminOrCoach(userId, clubId);
        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("Only the author or an admin can delete this comment");
        }

        boolean isTopLevel = existing.parentCommentId() == null;
        event.getComments().removeIf(c -> c.id().equals(commentId)
                || (isTopLevel && commentId.equals(c.parentCommentId())));
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        Map<String, Object> payload = new HashMap<>();
        payload.put("feedEventId", feedEventId);
        payload.put("commentId", commentId);
        broadcaster.broadcast(clubId, "comment_deleted", payload);
    }

    private ClubFeedEvent requireEvent(String clubId, String feedEventId) {
        return feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));
    }

    private ClubFeedEvent.CommentEntry findComment(ClubFeedEvent event, String commentId) {
        return event.getComments().stream()
                .filter(c -> c.id().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
    }

    private void notifyParentAndMentions(String clubId, String feedEventId, String commentId,
                                         String authorId, User author, String content,
                                         ClubFeedEvent.CommentEntry parent,
                                         List<ClubFeedEvent.MentionRef> mentions) {
        Set<String> notified = new HashSet<>();
        if (parent != null && !parent.userId().equals(authorId)) {
            notificationService.sendToUsers(List.of(parent.userId()),
                    "New reply",
                    author.getDisplayName() + " replied: " + truncate(content, NOTIFICATION_PREVIEW_CHARS),
                    Map.of("type", "CLUB_REPLY", "clubId", clubId, "feedEventId", feedEventId,
                            "commentId", commentId),
                    "clubReply");
            notified.add(parent.userId());
        }
        Set<String> mentionedIds = mentionResolver.idsOf(mentions);
        mentionedIds.remove(authorId);
        mentionedIds.removeAll(notified);
        if (!mentionedIds.isEmpty()) {
            notificationService.sendToUsers(new ArrayList<>(mentionedIds),
                    "You were mentioned",
                    author.getDisplayName() + " mentioned you: " + truncate(content, NOTIFICATION_PREVIEW_CHARS),
                    Map.of("type", "CLUB_MENTION", "clubId", clubId, "feedEventId", feedEventId,
                            "commentId", commentId),
                    "clubMention");
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
