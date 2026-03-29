package com.koval.trainingplannerbackend.club.tools;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;
import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupService;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.GroupLinkedTraining;
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

    private final ClubService clubService;
    private final ClubSessionService sessionService;
    private final RecurringSessionService recurringService;
    private final ClubMembershipService membershipService;
    private final ClubGroupService groupService;

    public ClubToolService(ClubService clubService,
                           ClubSessionService sessionService,
                           RecurringSessionService recurringService,
                           ClubMembershipService membershipService,
                           ClubGroupService groupService) {
        this.clubService = clubService;
        this.sessionService = sessionService;
        this.recurringService = recurringService;
        this.membershipService = membershipService;
        this.groupService = groupService;
    }

    // ── List user's clubs ─────────────────────────────────────────────

    @Tool(description = "List all clubs the current user is a member of. Returns club id, name, description, member count, and the user's membership status/role. Use this first to discover which clubs the user belongs to before performing club-specific operations.")
    public Object listMyClubs(
            @ToolParam(description = "User ID (from context)") String userId) {
        List<ClubSummaryResponse> clubs = clubService.getUserClubs(userId);
        return clubs.stream().map(c -> new MyClubSummary(
                c.id(), c.name(), c.description(), c.memberCount(), c.membershipStatus()
        )).toList();
    }

    // ── Session listing ──────────────────────────────────────────────

    @Tool(description = "List club training sessions in a date range. Returns summaries with id, title, sport, scheduledAt, location, participant count, linked training, and group.")
    public Object listClubSessions(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate to) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (from == null || to == null) return "Error: from and to dates are required.";

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();
        List<ClubTrainingSession> sessions = sessionService.listSessions(userId, clubId, fromDt, toDt);
        return sessions.stream().map(ClubToolService::toSessionSummary).toList();
    }

    // ── Create single session ────────────────────────────────────────

    @Tool(description = "Create a single club training session. Returns the created session summary.")
    public Object createClubSession(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session title") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, or BRICK") String sport,
            @ToolParam(description = "Scheduled date and time (ISO-8601, e.g. 2026-04-01T18:00)") LocalDateTime scheduledAt,
            @ToolParam(description = "Location (optional)") String location,
            @ToolParam(description = "Description (optional)") String description,
            @ToolParam(description = "Club group ID to restrict visibility (null = all members)") String clubGroupId,
            @ToolParam(description = "Max participants (null = unlimited)") Integer maxParticipants,
            @ToolParam(description = "Duration in minutes (optional)") Integer durationMinutes) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (title == null || title.isBlank()) return "Error: title is required.";
        if (scheduledAt == null) return "Error: scheduledAt is required.";

        var req = new CreateSessionRequest(null, title, sport, scheduledAt, location, null, null, description,
                null, maxParticipants, durationMinutes, clubGroupId, SecurityUtils.getCurrentUserId(),
                null, null, null);
        ClubTrainingSession session = sessionService.createSession(userId, clubId, req);
        return toSessionSummary(session);
    }

    // ── Create recurring session ─────────────────────────────────────

    @Tool(description = "Create a recurring session template that auto-generates sessions for the next 4 weeks. Returns the template summary.")
    public Object createRecurringSession(
            @ToolParam(description = "User ID (from context)") String userId,
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
            @ToolParam(description = "Duration in minutes (optional)") Integer durationMinutes) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (title == null || title.isBlank()) return "Error: title is required.";
        if (dayOfWeek == null) return "Error: dayOfWeek is required.";
        if (timeOfDay == null) return "Error: timeOfDay is required.";

        var req = new CreateRecurringSessionRequest(null, title, sport, dayOfWeek, timeOfDay, location, null, null, description,
                null, maxParticipants, durationMinutes, clubGroupId, SecurityUtils.getCurrentUserId(),
                null, null, null, endDate);
        RecurringSessionTemplate template = recurringService.createTemplate(userId, clubId, req);
        return toTemplateSummary(template);
    }

    // ── Cancel session ───────────────────────────────────────────────

    @Tool(description = "Cancel a club training session with a reason. Notifies all participants.")
    public Object cancelSession(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID to cancel") String sessionId,
            @ToolParam(description = "Cancellation reason") String reason) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (sessionId == null || sessionId.isBlank()) return "Error: sessionId is required.";

        sessionService.cancelEntireSession(userId, clubId, sessionId, reason);
        return "Session " + sessionId + " cancelled.";
    }

    // ── Cancel recurring series ──────────────────────────────────────

    @Tool(description = "Cancel all future instances of a recurring session template and deactivate it.")
    public Object cancelRecurringSeries(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Recurring template ID") String templateId,
            @ToolParam(description = "Cancellation reason") String reason) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (templateId == null || templateId.isBlank()) return "Error: templateId is required.";

        recurringService.cancelFutureInstances(userId, clubId, templateId, reason);
        return "Recurring series " + templateId + " cancelled. All future instances removed.";
    }

    // ── Link training to session ─────────────────────────────────────

    @Tool(description = "Link an existing training plan to a club session. Optionally link per club group.")
    public Object linkTrainingToSession(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Training ID to link") String trainingId,
            @ToolParam(description = "Club group ID for group-specific link (null = club-level)") String clubGroupId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (sessionId == null || sessionId.isBlank()) return "Error: sessionId is required.";
        if (trainingId == null || trainingId.isBlank()) return "Error: trainingId is required.";

        sessionService.linkTrainingToSession(userId, clubId, sessionId, trainingId, clubGroupId);
        return "Training " + trainingId + " linked to session " + sessionId + ".";
    }

    // ── Unlink training from session ─────────────────────────────────

    @Tool(description = "Remove a training link from a club session.")
    public Object unlinkTrainingFromSession(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Club group ID of the link to remove (null = club-level link)") String clubGroupId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (sessionId == null || sessionId.isBlank()) return "Error: sessionId is required.";

        sessionService.unlinkTrainingFromSession(userId, clubId, sessionId, clubGroupId);
        return "Training unlinked from session " + sessionId + ".";
    }

    // ── List members ─────────────────────────────────────────────────

    @Tool(description = "List active members of a club. Returns userId, displayName, role, and group tags.")
    public Object listClubMembers(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";

        List<ClubMemberResponse> members = membershipService.getMembers(userId, clubId);
        return members.stream().map(m -> new ClubMemberSummary(
                m.userId(), m.displayName(), m.role().name(), m.tags()
        )).toList();
    }

    // ── List groups ──────────────────────────────────────────────────

    @Tool(description = "List groups in a club. Returns group id, name, and member count.")
    public Object listClubGroups(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";

        List<ClubGroup> groups = groupService.listGroups(userId, clubId);
        return groups.stream().map(g -> new ClubGroupSummary(
                g.getId(), g.getName(), g.getMemberIds().size()
        )).toList();
    }

    // ── List recurring templates ──────────────────────────────────────

    @Tool(description = "List active recurring session templates for a club.")
    public Object listRecurringTemplates(
            @ToolParam(description = "User ID (from context)") String userId,
            @ToolParam(description = "Club ID") String clubId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";

        List<RecurringSessionTemplate> templates = recurringService.listTemplates(userId, clubId);
        return templates.stream().map(ClubToolService::toTemplateSummary).toList();
    }

    // ── Summary mappers ──────────────────────────────────────────────

    private static ClubSessionSummary toSessionSummary(ClubTrainingSession s) {
        String linkedTitle = null;
        List<GroupLinkedTraining> linked = s.getEffectiveLinkedTrainings();
        if (linked != null && !linked.isEmpty()) {
            linkedTitle = linked.stream()
                    .map(GroupLinkedTraining::getTrainingTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst().orElse(null);
        }
        return new ClubSessionSummary(
                s.getId(), s.getTitle(), s.getSport(),
                s.getScheduledAt(), s.getLocation(),
                s.getParticipantIds().size(), s.getMaxParticipants(),
                linkedTitle, s.getClubGroupId(),
                s.isCancelled(), s.getDurationMinutes()
        );
    }

    private static RecurringTemplateSummary toTemplateSummary(RecurringSessionTemplate t) {
        return new RecurringTemplateSummary(
                t.getId(), t.getTitle(), t.getSport(),
                t.getDayOfWeek(), t.getTimeOfDay(),
                t.getLocation(), t.isActive(),
                t.getClubGroupId(), t.getDurationMinutes()
        );
    }

    // ── Summary records ──────────────────────────────────────────────

    public record ClubSessionSummary(String id, String title, String sport,
                                     LocalDateTime scheduledAt, String location,
                                     int participantCount, Integer maxParticipants,
                                     String linkedTrainingTitle, String clubGroupId,
                                     boolean cancelled, Integer durationMinutes) {
    }

    public record RecurringTemplateSummary(String id, String title, String sport,
                                           DayOfWeek dayOfWeek, LocalTime timeOfDay,
                                           String location, boolean active,
                                           String clubGroupId, Integer durationMinutes) {
    }

    public record ClubMemberSummary(String userId, String displayName,
                                    String role, List<String> groupTags) {
    }

    public record ClubGroupSummary(String id, String name, int memberCount) {
    }

    public record MyClubSummary(String id, String name, String description,
                                int memberCount, String membershipStatus) {
    }
}
