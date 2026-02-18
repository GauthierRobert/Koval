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

    @Tool(description = "Assign a training to one or more athletes on a date.")
    public List<ScheduleSummary> assignTraining(
            @ToolParam(description = "Coach ID") String coachId,
            @ToolParam(description = "Training ID") String trainingId,
            @ToolParam(description = "Athlete IDs") List<String> athleteIds,
            @ToolParam(description = "Date (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Notes (optional)") String notes) {
        List<ScheduledWorkout> workouts = coachService.assignTraining(coachId, trainingId, athleteIds, scheduledDate, notes);
        String title = resolveTrainingTitle(trainingId);
        return workouts.stream().map(sw -> ScheduleSummary.from(sw, title)).toList();
    }

    @Tool(description = "Get an athlete's schedule in a date range.")
    public List<ScheduleSummary> getAthleteSchedule(
            @ToolParam(description = "Athlete ID") String athleteId,
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate start,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate end) {
        List<ScheduledWorkout> workouts = coachService.getAthleteSchedule(athleteId, start, end);
        return enrichWithTitles(workouts);
    }

    @Tool(description = "List athletes assigned to this coach.")
    public List<AthleteSummary> getCoachAthletes(
            @ToolParam(description = "Coach ID") String coachId) {
        return coachService.getCoachAthletes(coachId).stream()
                .map(AthleteSummary::from).toList();
    }

    @Tool(description = "List coach's athletes filtered by tag.")
    public List<AthleteSummary> getAthletesByTag(
            @ToolParam(description = "Coach ID") String coachId,
            @ToolParam(description = "Tag ID") String tagId) {
        return coachService.getAthletesByTag(coachId, tagId).stream()
                .map(AthleteSummary::from).toList();
    }

    @Tool(description = "List all tags for this coach. Call before getAthletesByTag.")
    public List<Tag> getAthleteTagsForCoach(
            @ToolParam(description = "Coach ID") String coachId) {
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
