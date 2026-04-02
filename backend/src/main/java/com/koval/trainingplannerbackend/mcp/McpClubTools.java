package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.SessionTrainingLinkService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * MCP tool adapter for club management operations.
 */
@Service
public class McpClubTools {

    private final ClubSessionService sessionService;
    private final SessionTrainingLinkService trainingLinkService;
    private final RecurringSessionService recurringService;
    private final ClubMembershipService membershipService;

    public McpClubTools(ClubSessionService sessionService,
                        SessionTrainingLinkService trainingLinkService,
                        RecurringSessionService recurringService,
                        ClubMembershipService membershipService) {
        this.sessionService = sessionService;
        this.trainingLinkService = trainingLinkService;
        this.recurringService = recurringService;
        this.membershipService = membershipService;
    }

    @Tool(description = "List club training sessions in a date range. Returns scheduled group workouts with title, sport, date/time, and participant info.")
    public Object listClubSessions(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate to) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (from == null || to == null) return "Error: from and to dates are required.";

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

    @Tool(description = "List active club members with their roles (OWNER, ADMIN, COACH, MEMBER).")
    public Object listClubMembers(
            @ToolParam(description = "Club ID") String clubId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        return membershipService.getMembers(userId, clubId);
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
                    s.getScheduledAt() != null ? s.getScheduledAt().toString() : null,
                    s.getLocation(), s.getMaxParticipants(),
                    s.getDurationMinutes(),
                    s.getParticipantIds() != null ? s.getParticipantIds().size() : 0);
        }
    }

    public record RecurringTemplateSummary(String id, String title, String sport,
                                            String dayOfWeek, String timeOfDay) {
        public static RecurringTemplateSummary from(RecurringSessionTemplate t) {
            return new RecurringTemplateSummary(
                    t.getId(), t.getTitle(), t.getSport(),
                    t.getDayOfWeek() != null ? t.getDayOfWeek().name() : null,
                    t.getTimeOfDay() != null ? t.getTimeOfDay().toString() : null);
        }
    }
}
