package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.club.dto.ClubDetailResponse;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
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

    public McpClubTools(ClubSessionService sessionService,
                        SessionTrainingLinkService trainingLinkService,
                        RecurringSessionService recurringService,
                        ClubMembershipService membershipService,
                        ClubService clubService,
                        SessionParticipationService participationService) {
        this.sessionService = sessionService;
        this.trainingLinkService = trainingLinkService;
        this.recurringService = recurringService;
        this.membershipService = membershipService;
        this.clubService = clubService;
        this.participationService = participationService;
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
