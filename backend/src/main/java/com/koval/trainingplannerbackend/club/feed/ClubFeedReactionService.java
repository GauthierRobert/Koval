package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.club.feed.dto.ReactionStateResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ReactionUpdatePayload;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight emoji reactions on feed events and on comments.
 * Distinct from {@code kudosGivenBy} (Strava-synced kudos), which is preserved as-is.
 */
@Service
public class ClubFeedReactionService {

    /** Server allowlist — keeps reactions tasteful and avoids unbounded emoji growth. */
    private static final Set<String> ALLOWED_EMOJI = Set.of(
            "fire", "muscle", "clap", "heart", "party", "raise");

    private final ClubFeedEventRepository feedEventRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubFeedSseBroadcaster broadcaster;

    public ClubFeedReactionService(ClubFeedEventRepository feedEventRepository,
                                   ClubAuthorizationService authorizationService,
                                   ClubFeedSseBroadcaster broadcaster) {
        this.feedEventRepository = feedEventRepository;
        this.authorizationService = authorizationService;
        this.broadcaster = broadcaster;
    }

    public ReactionStateResponse toggleEventReaction(String userId, String clubId,
                                                     String feedEventId, String emoji) {
        validateEmoji(emoji);
        authorizationService.requireActiveMember(userId, clubId);
        ClubFeedEvent event = loadEvent(clubId, feedEventId);

        Map<String, Set<String>> reactions = ensureReactions(event.getReactions());
        Set<String> users = reactions.computeIfAbsent(emoji, k -> new HashSet<>());
        boolean added = users.add(userId);
        if (!added) users.remove(userId);
        if (users.isEmpty()) reactions.remove(emoji);
        event.setReactions(reactions);
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        int count = users.size();
        broadcaster.broadcast(clubId, "reaction_update",
                new ReactionUpdatePayload(feedEventId, null, emoji, count, userId, added));
        return new ReactionStateResponse(feedEventId, null, emoji, count, added);
    }

    public ReactionStateResponse toggleCommentReaction(String userId, String clubId,
                                                       String feedEventId, String commentId, String emoji) {
        validateEmoji(emoji);
        authorizationService.requireActiveMember(userId, clubId);
        ClubFeedEvent event = loadEvent(clubId, feedEventId);

        ClubFeedEvent.CommentEntry existing = event.getComments().stream()
                .filter(c -> c.id().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        Map<String, Set<String>> reactions = ensureReactions(existing.reactions());
        Set<String> users = reactions.computeIfAbsent(emoji, k -> new HashSet<>());
        boolean added = users.add(userId);
        if (!added) users.remove(userId);
        if (users.isEmpty()) reactions.remove(emoji);

        ClubFeedEvent.CommentEntry updated = new ClubFeedEvent.CommentEntry(
                existing.id(), existing.userId(), existing.displayName(), existing.profilePicture(),
                existing.content(), existing.createdAt(), existing.updatedAt(),
                existing.parentCommentId(),
                reactions,
                existing.mentions() != null ? existing.mentions() : List.of());

        int idx = event.getComments().indexOf(existing);
        event.getComments().set(idx, updated);
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        int count = users.size();
        broadcaster.broadcast(clubId, "reaction_update",
                new ReactionUpdatePayload(feedEventId, commentId, emoji, count, userId, added));
        return new ReactionStateResponse(feedEventId, commentId, emoji, count, added);
    }

    private ClubFeedEvent loadEvent(String clubId, String feedEventId) {
        return feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));
    }

    private static Map<String, Set<String>> ensureReactions(Map<String, Set<String>> existing) {
        return existing == null ? new HashMap<>() : existing;
    }

    private static void validateEmoji(String emoji) {
        if (emoji == null || !ALLOWED_EMOJI.contains(emoji)) {
            throw new IllegalArgumentException("Unsupported reaction emoji");
        }
    }
}
