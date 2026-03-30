package com.koval.trainingplannerbackend.club.tools;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * AI-facing tool service for Club management operations.
 * Returns lean summaries to minimize token usage.
 */
@Service
public class ClubToolService {

    private final ClubSessionService sessionService;
    private final RecurringSessionService recurringService;
    private final ClubMembershipService membershipService;

    public ClubToolService(ClubSessionService sessionService,
                           RecurringSessionService recurringService,
                           ClubMembershipService membershipService) {
        this.sessionService = sessionService;
        this.recurringService = recurringService;
        this.membershipService = membershipService;
    }

    // ── Session listing ──────────────────────────────────────────────

    @Tool(description = "List club sessions in a date range.")
    public Object listClubSessions(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate to,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err != null) return err;
        if (from == null || to == null) return "Error: from and to dates are required.";

        String userId = SecurityUtils.getUserId(context);
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();
        List<ClubTrainingSession> sessions = sessionService.listSessions(userId, clubId, fromDt, toDt);
        return sessions.stream().map(ClubSessionSummary::from).toList();
    }

    // ── Create single session ────────────────────────────────────────

    @Tool(description = "Create a single club session.")
    public Object createClubSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session title") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, or BRICK") String sport,
            @ToolParam(description = "Scheduled date and time (ISO-8601, e.g. 2026-04-01T18:00)") LocalDateTime scheduledAt,
            @ToolParam(description = "Location (optional)") String location,
            @ToolParam(description = "Description (optional)") String description,
            @ToolParam(description = "Club group ID to restrict visibility (null = all members)") String clubGroupId,
            @ToolParam(description = "Max participants (null = unlimited)") Integer maxParticipants,
            @ToolParam(description = "Duration in minutes (optional)") Integer durationMinutes,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err == null) err = requireNonBlank(title, "title");
        if (err != null) return err;
        if (scheduledAt == null) return "Error: scheduledAt is required.";

        ToolEventEmitter.emitToolCall(context, "createClubSession", "Creating: " + title);
        String userId = SecurityUtils.getUserId(context);
        var req = new CreateSessionRequest(null, title, sport, scheduledAt, location, null, null, description,
                null, maxParticipants, durationMinutes, clubGroupId, userId,
                null, null, null);
        ClubTrainingSession session = sessionService.createSession(userId, clubId, req);
        ToolEventEmitter.emitToolResult(context, "createClubSession", title, true);
        return ClubSessionSummary.from(session);
    }

    // ── Create recurring session ─────────────────────────────────────

    @Tool(description = "Create a recurring weekly session (generates 4 weeks).")
    public Object createRecurringSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session title") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, or BRICK") String sport,
            @ToolParam(description = "Day of week: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY") DayOfWeek dayOfWeek,
            @ToolParam(description = "Time of day (HH:mm, e.g. 18:30)") LocalTime timeOfDay,
            @ToolParam(description = "Optional : End date: date when recurring session stops. (null = never)") LocalDate endDate,
            @ToolParam(description = "Location (optional)") String location,
            @ToolParam(description = "Description (optional)") String description,
            @ToolParam(description = "Club group ID to restrict visibility (null = all members)") String clubGroupId,
            @ToolParam(description = "Max participants (null = unlimited)") Integer maxParticipants,
            @ToolParam(description = "Duration in minutes (optional)") Integer durationMinutes,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err == null) err = requireNonBlank(title, "title");
        if (err != null) return err;
        if (dayOfWeek == null) return "Error: dayOfWeek is required.";
        if (timeOfDay == null) return "Error: timeOfDay is required.";

        ToolEventEmitter.emitToolCall(context, "createRecurringSession", "Creating recurring: " + title);
        String userId = SecurityUtils.getUserId(context);
        var req = new CreateRecurringSessionRequest(null, title, sport, dayOfWeek, timeOfDay, location, null, null, description,
                null, maxParticipants, durationMinutes, clubGroupId, userId,
                null, null, null, endDate);
        RecurringSessionTemplate template = recurringService.createTemplate(userId, clubId, req);
        ToolEventEmitter.emitToolResult(context, "createRecurringSession", title, true);
        return RecurringTemplateSummary.from(template);
    }

    // ── Cancel session ───────────────────────────────────────────────

    @Tool(description = "Cancel a club session (notifies participants).")
    public String cancelSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID to cancel") String sessionId,
            @ToolParam(description = "Cancellation reason") String reason,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err == null) err = requireNonBlank(sessionId, "sessionId");
        if (err != null) return err;

        ToolEventEmitter.emitToolCall(context, "cancelSession", "Cancelling session...");
        String userId = SecurityUtils.getUserId(context);
        sessionService.cancelEntireSession(userId, clubId, sessionId, reason);
        ToolEventEmitter.emitToolResult(context, "cancelSession", "Session cancelled", true);
        return "Session " + sessionId + " cancelled.";
    }

    // ── Cancel recurring series ──────────────────────────────────────

    @Tool(description = "Cancel all future instances of a recurring template.")
    public String cancelRecurringSeries(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Recurring template ID") String templateId,
            @ToolParam(description = "Cancellation reason") String reason,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err == null) err = requireNonBlank(templateId, "templateId");
        if (err != null) return err;

        ToolEventEmitter.emitToolCall(context, "cancelRecurringSeries", "Cancelling recurring series...");
        String userId = SecurityUtils.getUserId(context);
        recurringService.cancelFutureInstances(userId, clubId, templateId, reason);
        ToolEventEmitter.emitToolResult(context, "cancelRecurringSeries", "Series cancelled", true);
        return "Recurring series " + templateId + " cancelled. All future instances removed.";
    }

    // ── Link training to session ─────────────────────────────────────

    @Tool(description = "Link a training to a club session (optionally per group).")
    public String linkTrainingToSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Training ID to link") String trainingId,
            @ToolParam(description = "Club group ID for group-specific link (null = club-level)") String clubGroupId,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err == null) err = requireNonBlank(sessionId, "sessionId");
        if (err == null) err = requireNonBlank(trainingId, "trainingId");
        if (err != null) return err;

        ToolEventEmitter.emitToolCall(context, "linkTrainingToSession", "Linking training...");
        String userId = SecurityUtils.getUserId(context);
        sessionService.linkTrainingToSession(userId, clubId, sessionId, trainingId, clubGroupId);
        ToolEventEmitter.emitToolResult(context, "linkTrainingToSession", "Training linked", true);
        return "Training " + trainingId + " linked to session " + sessionId + ".";
    }

    // ── Unlink training from session ─────────────────────────────────

    @Tool(description = "Remove a training link from a session.")
    public String unlinkTrainingFromSession(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Group ID (null = club-level)") String clubGroupId,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err == null) err = requireNonBlank(sessionId, "sessionId");
        if (err != null) return err;

        ToolEventEmitter.emitToolCall(context, "unlinkTrainingFromSession", "Unlinking training...");
        String userId = SecurityUtils.getUserId(context);
        sessionService.unlinkTrainingFromSession(userId, clubId, sessionId, clubGroupId);
        ToolEventEmitter.emitToolResult(context, "unlinkTrainingFromSession", "Training unlinked", true);
        return "Training unlinked from session " + sessionId + ".";
    }

    // ── List members ─────────────────────────────────────────────────

    @Tool(description = "List active club members.")
    public Object listClubMembers(
            @ToolParam(description = "Club ID") String clubId,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err != null) return err;

        String userId = SecurityUtils.getUserId(context);
        List<ClubMemberResponse> members = membershipService.getMembers(userId, clubId);
        return members.stream().map(ClubMemberSummary::from).toList();
    }

    // ── List recurring templates ──────────────────────────────────────

    @Tool(description = "List recurring session templates for a club.")
    public Object listRecurringTemplates(
            @ToolParam(description = "Club ID") String clubId,
            ToolContext context) {
        String err = requireNonBlank(clubId, "clubId");
        if (err != null) return err;

        String userId = SecurityUtils.getUserId(context);
        List<RecurringSessionTemplate> templates = recurringService.listTemplates(userId, clubId);
        return templates.stream().map(RecurringTemplateSummary::from).toList();
    }

    // ── Validation helper ──────────────────────────────────────────────

    private static String requireNonBlank(String value, String fieldName) {
        return (value == null || value.isBlank()) ? "Error: " + fieldName + " is required." : null;
    }

}
