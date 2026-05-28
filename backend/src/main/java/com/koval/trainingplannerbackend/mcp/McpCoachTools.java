package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachNote;
import com.koval.trainingplannerbackend.coach.CoachNoteService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.dto.AthleteResponse;
import com.koval.trainingplannerbackend.config.Provenance;
import java.util.Map;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.history.AlignmentScore;
import com.koval.trainingplannerbackend.training.history.SessionAlignmentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * MCP tool adapter for coach-only operations that have no athlete-self equivalent.
 *
 * <p>Capabilities that exist for both an athlete (self) and a coach (managed athlete) — reading
 * PMC, schedule, sessions, etc. — live on their athlete-side adapter and take an optional
 * {@code athleteId} routed through {@link McpAccessResolver}, rather than being duplicated here.
 */
@Service
public class McpCoachTools {

    private final CoachService coachService;
    private final TrainingService trainingService;
    private final CoachNoteService coachNoteService;
    private final SessionAlignmentService alignmentService;

    public McpCoachTools(CoachService coachService,
                         TrainingService trainingService,
                         CoachNoteService coachNoteService,
                         SessionAlignmentService alignmentService) {
        this.coachService = coachService;
        this.trainingService = trainingService;
        this.coachNoteService = coachNoteService;
        this.alignmentService = alignmentService;
    }

    @Tool(description = "List all athletes coached by the current user. Returns each athlete's "
            + "anonymous alias (e.g. SwiftOtter-42) plus their FTP, weight, and performance metrics. "
            + "Real names are never returned — use the alias to refer to athletes back to the coach. "
            + "Requires COACH role.")
    public List<AthleteSummary> listAthletes() {
        SecurityUtils.requireCoach();
        String coachId = SecurityUtils.getCurrentUserId();
        return coachService.getAthletes(coachId).stream()
                .map(AthleteSummary::from)
                .toList();
    }

    /**
     * MCP-only athlete projection: alias and metrics, no real name / email / avatar.
     * Mirrors {@link AthleteResponse} minus the identifying fields so the same MCP tool
     * cannot leak PII even if the REST DTO grows new identity fields later.
     */
    public record AthleteSummary(String id, String alias, String role,
                                 Integer ftp, Integer weightKg,
                                 Integer functionalThresholdPace, Integer criticalSwimSpeed,
                                 Integer pace5k, Integer pace10k,
                                 Integer paceHalfMarathon, Integer paceMarathon,
                                 Integer vo2maxPower, Integer vo2maxPace,
                                 Map<String, Integer> customZoneReferenceValues,
                                 List<String> groups, List<String> clubs, boolean hasCoach) {
        public static AthleteSummary from(AthleteResponse a) {
            return new AthleteSummary(
                    a.id(), a.alias(), a.role(),
                    a.ftp(), a.weightKg(),
                    a.functionalThresholdPace(), a.criticalSwimSpeed(),
                    a.pace5k(), a.pace10k(),
                    a.paceHalfMarathon(), a.paceMarathon(),
                    a.vo2maxPower(), a.vo2maxPace(),
                    a.customZoneReferenceValues(),
                    a.groups(), a.clubs(), a.hasCoach());
        }
    }

    @Tool(description = "Assign a training workout to one or more athletes on a specific date. Requires COACH role.")
    public Object assignTraining(
            @ToolParam(description = "Training ID to assign") String trainingId,
            @ToolParam(description = "List of athlete IDs to assign to") List<String> athleteIds,
            @ToolParam(description = "Date to schedule (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Optional notes for the athletes") String notes) {
        SecurityUtils.requireCoach();
        if (trainingId == null || trainingId.isBlank()) return "Error: trainingId is required.";
        if (athleteIds == null || athleteIds.isEmpty()) return "Error: athleteIds list is required.";
        if (scheduledDate == null) return "Error: scheduledDate is required.";

        String coachId = SecurityUtils.getCurrentUserId();
        List<ScheduledWorkout> workouts = coachService.assignTraining(
                coachId, trainingId, athleteIds, scheduledDate, notes, null);
        String title = trainingService.getTrainingById(trainingId).getTitle();
        return workouts.stream()
                .map(sw -> McpSchedulingTools.ScheduleSummary.from(sw, title))
                .toList();
    }

    @Tool(description = "Append a coach note about an athlete you manage. Use this to leave AI-drafted " +
            "feedback after reviewing the athlete's recent work — the note shows up on the athlete's coach " +
            "view with an AI-source badge. Pass sessionId to tie the note to a specific completed session, " +
            "or omit it for a general note. When the note assesses how well a completed session matched the " +
            "workout that was scheduled for it, also pass alignmentScore (with sessionId): a percentage where " +
            "100 = exactly on plan, above 100 = the athlete exceeded the scheduled power/duration/TSS/IF/zone " +
            "targets, and below 100 = they fell short. The body becomes the reasoning behind the score. " +
            "Requires the current user to be the athlete's coach.")
    public CoachNoteSummary appendCoachNote(
            @ToolParam(description = "Athlete user ID") String athleteId,
            @ToolParam(description = "Note body in markdown (max 10000 chars)") String body,
            @ToolParam(description = "Optional completed session ID this note refers to") String sessionId,
            @ToolParam(required = false, description = "Optional alignment score as a percentage (0-300, "
                    + "100 = on plan). Requires sessionId. Sets the coach/AI rating shown on the session badge "
                    + "and the alignment evolution chart.") Integer alignmentScore) {
        SecurityUtils.requireCoach();
        String coachId = SecurityUtils.getCurrentUserId();
        CoachNote saved = coachNoteService.append(coachId, athleteId, sessionId, body, Provenance.mcp());
        if (alignmentScore != null && sessionId != null && !sessionId.isBlank()) {
            alignmentService.setCoachScore(sessionId, alignmentScore, body, AlignmentScore.SOURCE_AI, coachId);
        }
        return CoachNoteSummary.from(saved);
    }

    public record CoachNoteSummary(String id, String coachId, String athleteId, String sessionId,
                                    String body, String createdAt, String source) {
        public static CoachNoteSummary from(CoachNote n) {
            return new CoachNoteSummary(
                    n.getId(), n.getCoachId(), n.getAthleteId(), n.getSessionId(),
                    n.getBody(),
                    n.getCreatedAt() != null ? n.getCreatedAt().toString() : null,
                    n.getProvenance() != null ? n.getProvenance().source() : null);
        }
    }
}
