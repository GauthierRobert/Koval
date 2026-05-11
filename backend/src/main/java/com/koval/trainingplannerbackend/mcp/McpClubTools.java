package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.club.dto.ClubDetailResponse;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.feed.ClubEngagementInsightsService;
import com.koval.trainingplannerbackend.club.feed.ClubAnnouncementService;
import com.koval.trainingplannerbackend.club.feed.ClubFeedService;
import com.koval.trainingplannerbackend.club.feed.ClubFeedSpotlightService;
import com.koval.trainingplannerbackend.club.feed.SpotlightBadge;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedResponse;
import com.koval.trainingplannerbackend.club.feed.dto.CreateSpotlightRequest;
import com.koval.trainingplannerbackend.club.feed.dto.EngagementInsightsResponse;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.SessionParticipationService;
import com.koval.trainingplannerbackend.club.session.SessionTrainingLinkService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * MCP tool adapter for club management operations.
 */
@Service
public class McpClubTools {

    private final ClubSessionService sessionService;
    private final SessionTrainingLinkService trainingLinkService;
    private final RecurringSessionService recurringService;
    private final ClubMembershipService membershipService;
    private final ClubService clubService;
    private final SessionParticipationService participationService;
    private final ClubFeedService feedService;
    private final ClubAnnouncementService announcementService;
    private final ClubFeedSpotlightService spotlightService;
    private final ClubEngagementInsightsService insightsService;

    public McpClubTools(ClubSessionService sessionService,
                        SessionTrainingLinkService trainingLinkService,
                        RecurringSessionService recurringService,
                        ClubMembershipService membershipService,
                        ClubService clubService,
                        SessionParticipationService participationService,
                        ClubFeedService feedService,
                        ClubAnnouncementService announcementService,
                        ClubFeedSpotlightService spotlightService,
                        ClubEngagementInsightsService insightsService) {
        this.sessionService = sessionService;
        this.trainingLinkService = trainingLinkService;
        this.recurringService = recurringService;
        this.membershipService = membershipService;
        this.clubService = clubService;
        this.participationService = participationService;
        this.feedService = feedService;
        this.announcementService = announcementService;
        this.spotlightService = spotlightService;
        this.insightsService = insightsService;
    }

    private static final int MAX_SESSION_WINDOW_DAYS = 180;
    private static final int MAX_MEMBER_LIST_SIZE = 200;

    @Tool(description = "List club training sessions in a date range. Returns scheduled group workouts with title, sport, date/time, and participant info. The window is capped at 180 days; pass a tighter range for large clubs to avoid noisy results.")
    public Object listClubSessions(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate to) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (from == null || to == null) return "Error: from and to dates are required.";
        if (to.isBefore(from)) return "Error: 'to' must be on or after 'from'.";
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_SESSION_WINDOW_DAYS) {
            return "Error: range too wide — maximum " + MAX_SESSION_WINDOW_DAYS + " days. Narrow the window and call again.";
        }

        String userId = SecurityUtils.getCurrentUserId();
        List<ClubTrainingSession> sessions = sessionService.listSessions(
                userId, clubId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return sessions.stream().map(ClubSessionSummary::from).toList();
    }

    @Tool(description = "Create a single club training session. This is a one-time group workout event.")
    public Object createClubSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session title") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, or BRICK") String sport,
            @ToolParam(description = "Scheduled date and time (ISO-8601, e.g. 2026-04-15T18:00)") LocalDateTime scheduledAt,
            @ToolParam(description = "Location (optional)") String location,
            @ToolParam(description = "Description (optional)") String description,
            @ToolParam(description = "Max participants, null for unlimited") Integer maxParticipants,
            @ToolParam(description = "Duration in minutes (optional)") Integer durationMinutes) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (title == null || title.isBlank()) return "Error: title is required.";
        if (scheduledAt == null) return "Error: scheduledAt is required.";

        String userId = SecurityUtils.getCurrentUserId();
        var req = new CreateSessionRequest(null, title, sport, scheduledAt, location,
                null, null, description, null, maxParticipants, durationMinutes,
                null, userId, null, null, null);
        ClubTrainingSession session = sessionService.createSession(userId, clubId, req);
        return ClubSessionSummary.from(session);
    }

    @Tool(description = "Create a recurring weekly club session that auto-generates instances (4 weeks ahead).")
    public Object createRecurringSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session title") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, or BRICK") String sport,
            @ToolParam(description = "Day of week: MONDAY-SUNDAY") DayOfWeek dayOfWeek,
            @ToolParam(description = "Time of day (HH:mm, e.g. 18:30)") LocalTime timeOfDay,
            @ToolParam(description = "Location (optional)") String location,
            @ToolParam(description = "Description (optional)") String description,
            @ToolParam(description = "Max participants, null for unlimited") Integer maxParticipants,
            @ToolParam(description = "Duration in minutes (optional)") Integer durationMinutes) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (title == null || title.isBlank()) return "Error: title is required.";
        if (dayOfWeek == null) return "Error: dayOfWeek is required.";
        if (timeOfDay == null) return "Error: timeOfDay is required.";

        String userId = SecurityUtils.getCurrentUserId();
        var req = new CreateRecurringSessionRequest(null, title, sport, dayOfWeek, timeOfDay,
                location, null, null, description, null, maxParticipants, durationMinutes,
                null, userId, null, null, null, null);
        RecurringSessionTemplate template = recurringService.createTemplate(userId, clubId, req);
        return RecurringTemplateSummary.from(template);
    }

    @Tool(description = "Cancel a club session (notifies participants).")
    public String cancelSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID to cancel") String sessionId,
            @ToolParam(description = "Cancellation reason") String reason) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (sessionId == null || sessionId.isBlank()) return "Error: sessionId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        sessionService.cancelEntireSession(userId, clubId, sessionId, reason);
        return "Session cancelled.";
    }

    @Tool(description = "List active club members with their roles (OWNER, ADMIN, COACH, MEMBER). For very large clubs the list is truncated to the first 200 members; ask for a specific name or role if you need more.")
    public Object listClubMembers(
            @ToolParam(description = "Club ID") String clubId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        List<ClubMemberResponse> members = membershipService.getMembers(userId, clubId);
        if (members.size() <= MAX_MEMBER_LIST_SIZE) return members;
        return members.subList(0, MAX_MEMBER_LIST_SIZE);
    }

    @Tool(description = "Get full detail of a single club: name, description, location, logo, visibility, member count, owner, and the current user's membership status/role within it.")
    public ClubDetailResponse getClub(
            @ToolParam(description = "Club ID") String clubId) {
        if (clubId == null || clubId.isBlank()) throw new IllegalArgumentException("clubId is required.");
        String userId = SecurityUtils.getCurrentUserId();
        return clubService.getClubDetail(clubId, userId);
    }

    @Tool(description = "Join a club training session as a participant. If the session is full the user is added to the waiting list and will be auto-promoted when a spot opens.")
    public String joinClubSession(
            @ToolParam(description = "Club ID (used for authorization context)") String clubId,
            @ToolParam(description = "Session ID to join") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "Error: sessionId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        ClubTrainingSession s = participationService.joinSession(userId, sessionId);
        return "Joined session '" + s.getTitle() + "'.";
    }

    @Tool(description = "Leave a club training session (or remove yourself from its waiting list).")
    public String leaveClubSession(
            @ToolParam(description = "Club ID (used for authorization context)") String clubId,
            @ToolParam(description = "Session ID to leave") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "Error: sessionId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        participationService.cancelSessionParticipation(userId, sessionId);
        return "Left session.";
    }

    @Tool(description = "Get the recent activity feed for a club: pinned events first then chronological. Items include session created/joined, member joined, training shared, etc.")
    public ClubFeedResponse getClubFeed(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Page size (default 20, max 100)") Integer limit) {
        if (clubId == null || clubId.isBlank()) throw new IllegalArgumentException("clubId is required.");
        String userId = SecurityUtils.getCurrentUserId();
        int size = (limit != null && limit > 0) ? Math.min(limit, 100) : 20;
        return feedService.getFeed(userId, clubId, 0, size);
    }

    @Tool(description = "Post a coach announcement to a club's feed. Coach/admin only. Returns the created feed event. Use to share news, motivation, schedule changes; supports optional @mentions of specific members by their userId.")
    public ClubFeedEventResponse postClubAnnouncement(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Announcement text content") String content,
            @ToolParam(description = "Optional list of user IDs to @mention (notifies those members)") List<String> mentionUserIds) {
        if (clubId == null || clubId.isBlank()) throw new IllegalArgumentException("clubId is required.");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required.");
        String userId = SecurityUtils.getCurrentUserId();
        return announcementService.create(userId, clubId, content, List.of(),
                mentionUserIds == null ? List.of() : mentionUserIds);
    }

    @Tool(description = "Spotlight a club member with a curated highlight that pins to the top of the feed for a configurable window. Coach/admin only. Use for milestones, comebacks, PRs, new members, grit moments. Badges: MILESTONE, COMEBACK, NEW_MEMBER, PR, GRIT, CUSTOM.")
    public ClubFeedEventResponse createMemberSpotlight(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "User ID of the member to spotlight") String spotlightedUserId,
            @ToolParam(description = "Short headline title (e.g. 'Sub-3 marathon!')") String title,
            @ToolParam(description = "Body message celebrating the member") String message,
            @ToolParam(description = "Badge: MILESTONE, COMEBACK, NEW_MEMBER, PR, GRIT, or CUSTOM") SpotlightBadge badge,
            @ToolParam(description = "How many days to keep the spotlight pinned (default 7, max 30)") Integer expiresInDays) {
        if (clubId == null || clubId.isBlank()) throw new IllegalArgumentException("clubId is required.");
        if (spotlightedUserId == null || spotlightedUserId.isBlank()) {
            throw new IllegalArgumentException("spotlightedUserId is required.");
        }
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required.");
        if (badge == null) throw new IllegalArgumentException("badge is required.");
        String userId = SecurityUtils.getCurrentUserId();
        var req = new CreateSpotlightRequest(spotlightedUserId, title, message, badge,
                List.of(), expiresInDays, List.of());
        return spotlightService.createSpotlight(userId, clubId, req);
    }

    @Tool(description = "Get per-member engagement insights for a club: comments posted, reactions given, sessions completed, and last activity over a lookback window. Coach/admin only. Use to identify dormant members worth re-engaging.")
    public EngagementInsightsResponse getEngagementInsights(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Lookback window in days (default 30, max 180)") Integer days) {
        if (clubId == null || clubId.isBlank()) throw new IllegalArgumentException("clubId is required.");
        String userId = SecurityUtils.getCurrentUserId();
        return insightsService.getInsights(userId, clubId, days != null ? days : 30);
    }

    @Tool(description = "Link a training workout to a club session so participants can follow the structured workout.")
    public String linkTrainingToSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Training ID to link") String trainingId,
            @ToolParam(description = "Club group ID for group-specific link, null for all") String clubGroupId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (sessionId == null || sessionId.isBlank()) return "Error: sessionId is required.";
        if (trainingId == null || trainingId.isBlank()) return "Error: trainingId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        trainingLinkService.linkTrainingToSession(userId, clubId, sessionId, trainingId, clubGroupId);
        return "Training linked to session.";
    }

    public record ClubSessionSummary(String id, String title, String sport, String scheduledAt,
                                      String location, Integer maxParticipants,
                                      Integer durationMinutes, int participantCount) {
        public static ClubSessionSummary from(ClubTrainingSession s) {
            return new ClubSessionSummary(
                    s.getId(), s.getTitle(), s.getSport(),
                    Optional.ofNullable(s.getScheduledAt()).map(LocalDateTime::toString).orElse(null),
                    s.getLocation(), s.getMaxParticipants(),
                    s.getDurationMinutes(),
                    Optional.ofNullable(s.getParticipantIds()).map(List::size).orElse(0));
        }
    }

    public record RecurringTemplateSummary(String id, String title, String sport,
                                            String dayOfWeek, String timeOfDay) {
        public static RecurringTemplateSummary from(RecurringSessionTemplate t) {
            return new RecurringTemplateSummary(
                    t.getId(), t.getTitle(), t.getSport(),
                    Optional.ofNullable(t.getDayOfWeek()).map(DayOfWeek::name).orElse(null),
                    Optional.ofNullable(t.getTimeOfDay()).map(LocalTime::toString).orElse(null));
        }
    }
}
