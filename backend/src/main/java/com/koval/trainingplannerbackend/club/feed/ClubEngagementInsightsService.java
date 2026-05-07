package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.EngagementInsightsResponse;
import com.koval.trainingplannerbackend.club.feed.dto.EngagementInsightsResponse.MemberEngagement;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes per-member engagement metrics over a configurable lookback window.
 * Coach/admin only; helps identify dormant members worth re-engaging.
 *
 * Counts come from existing collections (no new persistence): comments and
 * reactions live denormalized inside {@code club_feed_events}; sessions live
 * in {@code completed_sessions}.
 */
@Service
public class ClubEngagementInsightsService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 180;

    private final ClubAuthorizationService authorizationService;
    private final ClubMembershipRepository membershipRepository;
    private final ClubFeedEventRepository feedEventRepository;
    private final CompletedSessionRepository completedSessionRepository;
    private final UserService userService;

    public ClubEngagementInsightsService(ClubAuthorizationService authorizationService,
                                         ClubMembershipRepository membershipRepository,
                                         ClubFeedEventRepository feedEventRepository,
                                         CompletedSessionRepository completedSessionRepository,
                                         UserService userService) {
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.feedEventRepository = feedEventRepository;
        this.completedSessionRepository = completedSessionRepository;
        this.userService = userService;
    }

    public EngagementInsightsResponse getInsights(String userId, String clubId, int days) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        int window = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(window);

        List<ClubMembership> activeMembers = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);
        if (activeMembers.isEmpty()) {
            return new EngagementInsightsResponse(List.of(), window, LocalDateTime.now());
        }

        List<String> memberIds = activeMembers.stream().map(ClubMembership::getUserId).toList();
        Map<String, User> userById = new HashMap<>();
        for (User u : userService.findAllById(memberIds)) {
            userById.put(u.getId(), u);
        }

        // Comments + reactions: scan recent feed events for the club.
        Map<String, Integer> commentsByUser = new HashMap<>();
        Map<String, Integer> reactionsByUser = new HashMap<>();
        Map<String, LocalDateTime> lastActiveByUser = new HashMap<>();

        List<ClubFeedEvent> recentEvents = feedEventRepository.findByClubIdAndCreatedAtAfter(clubId, cutoff);
        for (ClubFeedEvent event : recentEvents) {
            // Comments / replies posted in window.
            if (event.getComments() != null) {
                for (ClubFeedEvent.CommentEntry c : event.getComments()) {
                    if (c.createdAt() != null && c.createdAt().isAfter(cutoff)) {
                        commentsByUser.merge(c.userId(), 1, Integer::sum);
                        bumpLastActive(lastActiveByUser, c.userId(), c.createdAt());
                    }
                    // Reactions on the comment.
                    if (c.reactions() != null) {
                        for (Set<String> users : c.reactions().values()) {
                            for (String uid : users) {
                                reactionsByUser.merge(uid, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
            // Reactions on the event itself.
            if (event.getReactions() != null) {
                for (Set<String> users : event.getReactions().values()) {
                    for (String uid : users) {
                        reactionsByUser.merge(uid, 1, Integer::sum);
                    }
                }
            }
        }

        // Sessions completed within window for these members.
        Map<String, Integer> sessionsByUser = new HashMap<>();
        List<CompletedSession> recentSessions = completedSessionRepository.findByUserIdInAndCompletedAtBetween(
                memberIds, cutoff, LocalDateTime.now());
        for (CompletedSession s : recentSessions) {
            sessionsByUser.merge(s.getUserId(), 1, Integer::sum);
            if (s.getCompletedAt() != null) {
                bumpLastActive(lastActiveByUser, s.getUserId(), s.getCompletedAt());
            }
        }

        List<MemberEngagement> rows = activeMembers.stream()
                .map(m -> {
                    User u = userById.get(m.getUserId());
                    String displayName = u != null && u.getDisplayName() != null
                            ? u.getDisplayName() : "Unknown";
                    String pic = u != null ? u.getProfilePicture() : null;
                    return new MemberEngagement(
                            m.getUserId(),
                            displayName,
                            pic,
                            m.getRole().name(),
                            commentsByUser.getOrDefault(m.getUserId(), 0),
                            reactionsByUser.getOrDefault(m.getUserId(), 0),
                            sessionsByUser.getOrDefault(m.getUserId(), 0),
                            lastActiveByUser.get(m.getUserId()));
                })
                .toList();

        return new EngagementInsightsResponse(rows, window, LocalDateTime.now());
    }

    private static void bumpLastActive(Map<String, LocalDateTime> map, String userId, LocalDateTime when) {
        LocalDateTime cur = map.get(userId);
        if (cur == null || when.isAfter(cur)) map.put(userId, when);
    }

    static int defaultDays() {
        return DEFAULT_DAYS;
    }
}
