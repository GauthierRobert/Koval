package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Keeps the calendar of {@link ScheduledWorkout}s in sync with a {@link TrainingPlan}'s
 * week/day structure: generation on activation, sync on edits, cancellation on pause,
 * pending cleanup on delete. Pure schedule-side logic — no plan state mutation.
 */
@Service
public class PlanScheduleSyncService {

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;

    public PlanScheduleSyncService(ScheduledWorkoutRepository scheduledWorkoutRepository,
                                   TrainingRepository trainingRepository) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
    }

    /** Fetches every distinct training referenced by a plan in a single query. */
    public Map<String, Training> fetchTrainingsForPlan(TrainingPlan plan) {
        List<String> ids = plan.getWeeks().stream()
                .flatMap(w -> w.getDays().stream())
                .map(PlanDay::getTrainingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        return StreamSupport.stream(trainingRepository.findAllById(ids).spliterator(), false)
                .collect(Collectors.toMap(Training::getId, t -> t, (a, b) -> a));
    }

    /**
     * Rebuilds future PENDING scheduled workouts from the plan's current structure.
     * Past/completed/skipped days are preserved. Used when an active plan is edited.
     */
    public void syncFutureScheduledWorkouts(TrainingPlan plan, String fallbackAthleteId) {
        LocalDate today = LocalDate.now();

        List<String> futurePendingIds = scheduledWorkoutRepository.findByPlanId(plan.getId()).stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING
                        && !sw.getScheduledDate().isBefore(today))
                .map(ScheduledWorkout::getId)
                .toList();
        scheduledWorkoutRepository.deleteAllById(futurePendingIds);

        plan.getWeeks().forEach(week -> week.getDays().forEach(day -> {
            LocalDate date = computeDate(plan.getStartDate(), week.getWeekNumber(), day.getDayOfWeek());
            if (!date.isBefore(today)) day.setScheduledWorkoutId(null);
        }));

        List<String> targetAthleteIds = plan.getAthleteIds().isEmpty()
                ? List.of(fallbackAthleteId)
                : plan.getAthleteIds();

        buildAndPersistScheduledWorkouts(plan, targetAthleteIds, fallbackAthleteId, today, fetchTrainingsForPlan(plan));
    }

    /**
     * Builds {@link ScheduledWorkout}s for every (week, day, athlete) combination scheduled on or
     * after {@code cutoff}, persists them in one batch, and links the saved IDs back onto each
     * {@link PlanDay}. When multiple athletes share a day, the last athlete's id wins on the day —
     * matching the previous imperative behaviour.
     */
    public void buildAndPersistScheduledWorkouts(TrainingPlan plan, List<String> athleteIds,
                                                 String assignedBy, LocalDate cutoff,
                                                 Map<String, Training> trainingById) {
        record DayWorkout(PlanDay day, ScheduledWorkout sw) {}

        List<DayWorkout> pairs = plan.getWeeks().stream()
                .flatMap(week -> week.getDays().stream()
                        .filter(day -> day.getTrainingId() != null)
                        .map(day -> Map.entry(week, day)))
                .filter(e -> !computeDate(plan.getStartDate(), e.getKey().getWeekNumber(),
                        e.getValue().getDayOfWeek()).isBefore(cutoff))
                .flatMap(e -> {
                    PlanDay day = e.getValue();
                    LocalDate date = computeDate(plan.getStartDate(), e.getKey().getWeekNumber(), day.getDayOfWeek());
                    Training training = trainingById.get(day.getTrainingId());
                    return athleteIds.stream().map(athleteId ->
                            new DayWorkout(day, newScheduledWorkout(plan.getId(), day, athleteId,
                                    assignedBy, date, training)));
                })
                .toList();

        if (pairs.isEmpty()) return;

        List<ScheduledWorkout> saved = scheduledWorkoutRepository.saveAll(
                pairs.stream().map(DayWorkout::sw).toList());
        for (int i = 0; i < pairs.size(); i++) {
            pairs.get(i).day().setScheduledWorkoutId(saved.get(i).getId());
        }
    }

    /**
     * Cancel future PENDING scheduled workouts for the plan, and clear the corresponding
     * scheduledWorkoutId references on cancelled days. Past days remain untouched.
     */
    public void cancelFuturePendingForPlan(TrainingPlan plan) {
        LocalDate today = LocalDate.now();

        List<String> futureIds = scheduledWorkoutRepository.findByPlanId(plan.getId()).stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING
                        && sw.getScheduledDate().isAfter(today))
                .map(ScheduledWorkout::getId)
                .toList();
        scheduledWorkoutRepository.deleteAllById(futureIds);

        plan.getWeeks().forEach(week -> week.getDays().stream()
                .filter(day -> day.getScheduledWorkoutId() != null)
                .filter(day -> computeDate(plan.getStartDate(), week.getWeekNumber(), day.getDayOfWeek()).isAfter(today))
                .forEach(day -> day.setScheduledWorkoutId(null)));
    }

    /** Delete only PENDING scheduled workouts for the plan; completed/skipped history is kept. */
    public void deletePendingForPlan(String planId) {
        List<String> pendingIds = scheduledWorkoutRepository.findByPlanId(planId).stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING)
                .map(ScheduledWorkout::getId)
                .toList();
        scheduledWorkoutRepository.deleteAllById(pendingIds);
    }

    private static ScheduledWorkout newScheduledWorkout(String planId, PlanDay day, String athleteId,
                                                        String assignedBy, LocalDate date, Training training) {
        ScheduledWorkout sw = new ScheduledWorkout();
        sw.setTrainingId(day.getTrainingId());
        sw.setAthleteId(athleteId);
        sw.setAssignedBy(assignedBy);
        sw.setScheduledDate(date);
        sw.setNotes(day.getNotes());
        sw.setPlanId(planId);
        sw.setStatus(ScheduleStatus.PENDING);
        if (training != null && training.getEstimatedTss() != null) {
            sw.setTss(training.getEstimatedTss());
            sw.setIntensityFactor(training.getEstimatedIf());
        }
        return sw;
    }

    static LocalDate computeDate(LocalDate planStartDate, int weekNumber, DayOfWeek dayOfWeek) {
        LocalDate weekStart = planStartDate.plusWeeks(weekNumber - 1);
        LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.plusDays(dayOfWeek.getValue() - 1);
    }
}
