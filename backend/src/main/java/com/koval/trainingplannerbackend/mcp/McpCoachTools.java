package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachNote;
import com.koval.trainingplannerbackend.coach.CoachNoteService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.dto.AthleteResponse;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.training.TrainingService;
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

    public McpCoachTools(CoachService coachService,
                         TrainingService trainingService,
                         CoachNoteService coachNoteService) {
        this.coachService = coachService;
        this.trainingService = trainingService;
        this.coachNoteService = coachNoteService;
    }

    @Tool(description = "List all athletes coached by the current user. Returns athlete profiles with FTP, weight, and performance metrics. Requires COACH role.")
    public List<AthleteResponse> listAthletes() {
        SecurityUtils.requireCoach();
        String coachId = SecurityUtils.getCurrentUserId();
        return coachService.getAthletes(coachId);
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
            "or omit it for a general note. Requires the current user to be the athlete's coach.")
    public CoachNoteSummary appendCoachNote(
            @ToolParam(description = "Athlete user ID") String athleteId,
            @ToolParam(description = "Note body in markdown (max 10000 chars)") String body,
            @ToolParam(description = "Optional completed session ID this note refers to") String sessionId) {
        SecurityUtils.requireCoach();
        String coachId = SecurityUtils.getCurrentUserId();
        CoachNote saved = coachNoteService.append(coachId, athleteId, sessionId, body, Provenance.mcp());
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
