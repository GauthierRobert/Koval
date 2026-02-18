package com.koval.trainingplannerbackend.coach.tools;

import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.tag.Tag;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI-facing tool service for Coach operations.
 * Returns lean summaries to minimize token usage.
 */
@Service
public class CoachToolService {

    private final CoachService coachService;
    private final TrainingRepository trainingRepository;

    public CoachToolService(CoachService coachService, TrainingRepository trainingRepository) {
        this.coachService = coachService;
        this.trainingRepository = trainingRepository;
    }

    @Tool(description = "Assign a training plan to one or more athletes on a specific date. Returns schedule summaries.")
    public List<ScheduleSummary> assignTraining(
            @ToolParam(description = "The coach's user ID") String coachId,
            @ToolParam(description = "The training ID to assign") String trainingId,
            @ToolParam(description = "List of athlete user IDs") List<String> athleteIds,
            @ToolParam(description = "The date to schedule (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Optional notes for the assignment") String notes) {
        List<ScheduledWorkout> workouts = coachService.assignTraining(coachId, trainingId, athleteIds, scheduledDate, notes);
        String title = resolveTrainingTitle(trainingId);
        return workouts.stream().map(sw -> ScheduleSummary.from(sw, title)).toList();
    }

    @Tool(description = "Assign a training plan to yourself (the current user) on a specific date. Any user can use this, regardless of role.")
    public ScheduleSummary selfAssignTraining(
            @ToolParam(description = "The user ID") String userId,
            @ToolParam(description = "The training ID to assign") String trainingId,
            @ToolParam(description = "The date to schedule (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Optional notes") String notes) {
        ScheduledWorkout sw = coachService.selfAssignTraining(userId, trainingId, scheduledDate, notes);
        return ScheduleSummary.from(sw, resolveTrainingTitle(trainingId));
    }

    @Tool(description = "Get the training schedule for a specific athlete within a date range. Returns summaries with training titles.")
    public List<ScheduleSummary> getAthleteSchedule(
            @ToolParam(description = "The athlete's user ID") String athleteId,
            @ToolParam(description = "Start date (inclusive, YYYY-MM-DD)") LocalDate start,
            @ToolParam(description = "End date (inclusive, YYYY-MM-DD)") LocalDate end) {
        List<ScheduledWorkout> workouts = coachService.getAthleteSchedule(athleteId, start, end);
        return enrichWithTitles(workouts);
    }

    @Tool(description = "Get the list of athletes assigned to a specific coach. Returns lean summaries (no tokens/emails).")
    public List<AthleteSummary> getCoachAthletes(
            @ToolParam(description = "The coach's user ID") String coachId) {
        return coachService.getCoachAthletes(coachId).stream()
                .map(AthleteSummary::from).toList();
    }

    @Tool(description = "Get athletes for a coach filtered by a specific tag ID. Returns lean summaries.")
    public List<AthleteSummary> getAthletesByTag(
            @ToolParam(description = "The coach's user ID") String coachId,
            @ToolParam(description = "The tag ID to filter by") String tagId) {
        return coachService.getAthletesByTag(coachId, tagId).stream()
                .map(AthleteSummary::from).toList();
    }

    @Tool(description = "Get all tags for a coach. Use this to discover what tags exist before filtering by tag.")
    public List<Tag> getAthleteTagsForCoach(
            @ToolParam(description = "The coach's user ID") String coachId) {
        return coachService.getAthleteTagsForCoach(coachId);
    }

    private List<ScheduleSummary> enrichWithTitles(List<ScheduledWorkout> workouts) {
        if (workouts.isEmpty()) return List.of();

        List<String> trainingIds = workouts.stream()
                .map(ScheduledWorkout::getTrainingId).distinct().toList();
        Map<String, String> titleMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Training::getTitle, (a, b) -> a));

        return workouts.stream()
                .map(sw -> ScheduleSummary.from(sw, titleMap.getOrDefault(sw.getTrainingId(), "Unknown")))
                .toList();
    }

    private String resolveTrainingTitle(String trainingId) {
        return trainingRepository.findById(trainingId)
                .map(Training::getTitle).orElse("Unknown");
    }
}
