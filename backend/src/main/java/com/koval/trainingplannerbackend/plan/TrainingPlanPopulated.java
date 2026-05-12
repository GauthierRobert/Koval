package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Populated view of a TrainingPlan with Training objects resolved from their IDs.
 */
public record TrainingPlanPopulated(
        String id,
        String title,
        String description,
        SportType sportType,
        String createdBy,
        LocalDate startDate,
        int durationWeeks,
        PlanStatus status,
        List<WeekPopulated> weeks,
        String goalRaceId,
        Integer targetFtp,
        List<String> athleteIds,
        LocalDateTime createdAt,
        LocalDateTime activatedAt
) {

    public record DayPopulated(
            DayOfWeek dayOfWeek,
            List<String> trainingIds,
            String notes,
            List<String> scheduledWorkoutIds,
            List<Training> trainings
    ) {}

    public record WeekPopulated(
            int weekNumber,
            String label,
            Integer targetTss,
            List<DayPopulated> days
    ) {}

    /**
     * Builds a populated view from a TrainingPlan and a map of training IDs to Training objects.
     */
    public static TrainingPlanPopulated from(TrainingPlan plan, Map<String, Training> trainingsById) {
        List<WeekPopulated> populatedWeeks = plan.getWeeks().stream()
                .map(week -> new WeekPopulated(
                        week.getWeekNumber(),
                        week.getLabel(),
                        week.getTargetTss(),
                        week.getDays().stream()
                                .map(day -> new DayPopulated(
                                        day.getDayOfWeek(),
                                        List.copyOf(day.getTrainingIds()),
                                        day.getNotes(),
                                        List.copyOf(day.getScheduledWorkoutIds()),
                                        day.getTrainingIds().stream()
                                                .map(trainingsById::get)
                                                .filter(Objects::nonNull)
                                                .toList()
                                ))
                                .toList()
                ))
                .toList();

        return new TrainingPlanPopulated(
                plan.getId(),
                plan.getTitle(),
                plan.getDescription(),
                plan.getSportType(),
                plan.getCreatedBy(),
                plan.getStartDate(),
                plan.getDurationWeeks(),
                plan.getStatus(),
                populatedWeeks,
                plan.getGoalRaceId(),
                plan.getTargetFtp(),
                plan.getAthleteIds(),
                plan.getCreatedAt(),
                plan.getActivatedAt()
        );
    }

    /**
     * Collects all unique non-null training IDs from a plan.
     */
    public static List<String> collectTrainingIds(TrainingPlan plan) {
        return plan.getWeeks().stream()
                .flatMap(week -> week.getDays().stream())
                .flatMap(day -> day.getTrainingIds().stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
