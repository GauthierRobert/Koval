package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only analytics over training plans: progress counts, weekly TSS breakdown,
 * active-plan dashboard summary, per-athlete progress for the coach view.
 *
 * <p>Lives alongside {@link TrainingPlanService} (which owns lifecycle / writes) but
 * intentionally does not modify any state.
 */
@Service
public class PlanAnalyticsService {

    private final TrainingPlanRepository planRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;

    public PlanAnalyticsService(TrainingPlanRepository planRepository,
                                ScheduledWorkoutRepository scheduledWorkoutRepository) {
        this.planRepository = planRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
    }

    /**
     * Returns plan summaries for an athlete including progress and analytics,
     * used by the coach dashboard.
     */
    public List<AthletePlanSummary> listPlansByAthleteWithProgress(String athleteId) {
        List<TrainingPlan> plans = planRepository.findByAthleteIdsContaining(athleteId);
        if (plans.isEmpty()) return List.of();

        List<String> planIds = plans.stream().map(TrainingPlan::getId).toList();
        Map<String, List<ScheduledWorkout>> workoutsByPlan = scheduledWorkoutRepository
                .findByPlanIdIn(planIds).stream()
                .collect(Collectors.groupingBy(ScheduledWorkout::getPlanId));

        return plans.stream()
                .map(plan -> {
                    List<ScheduledWorkout> workouts = workoutsByPlan.getOrDefault(plan.getId(), List.of());
                    return AthletePlanSummary.from(plan,
                            computeCurrentWeek(plan.getStartDate()),
                            computeProgress(plan, workouts),
                            computeAnalytics(plan, workouts));
                })
                .toList();
    }

    /**
     * Returns a lightweight summary of the user's active plan for dashboard display.
     * Checks plans created by the user and plans assigned to the user as athlete.
     */
    public ActivePlanSummary getActivePlan(String userId) {
        TrainingPlan plan = planRepository.findByCreatedByAndStatus(userId, PlanStatus.ACTIVE).stream()
                .findFirst()
                .or(() -> planRepository.findByAthleteIdsContaining(userId).stream()
                        .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                        .findFirst())
                .orElse(null);

        if (plan == null) return null;

        int currentWeek = computeCurrentWeek(plan.getStartDate());
        List<ScheduledWorkout> planWorkouts = scheduledWorkoutRepository.findByPlanId(plan.getId());

        // Build scheduledWorkoutId → weekNumber index. Two scheduled workouts on the
        // same day across weeks would collide; ((a, b) -> a) keeps the first week seen.
        Map<String, Integer> swToWeek = plan.getWeeks().stream()
                .flatMap(week -> week.getDays().stream()
                        .flatMap(day -> day.getScheduledWorkoutIds().stream()
                                .map(swId -> Map.entry(swId, week.getWeekNumber()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

        PlanWeek currentPlanWeek = plan.getWeeks().stream()
                .filter(w -> w.getWeekNumber() == currentWeek)
                .findFirst()
                .orElse(null);

        int weekRemaining = 0;
        int weekActualTss = 0;
        int totalCompleted = 0;

        for (ScheduledWorkout sw : planWorkouts) {
            if (sw.getStatus() == ScheduleStatus.COMPLETED) totalCompleted++;
            Integer weekNum = swToWeek.get(sw.getId());
            if (weekNum != null && weekNum == currentWeek) {
                if (sw.getStatus() == ScheduleStatus.PENDING) weekRemaining++;
                if (sw.getStatus() == ScheduleStatus.COMPLETED && sw.getTss() != null) weekActualTss += sw.getTss();
            }
        }

        int completionPercent = planWorkouts.isEmpty() ? 0 : 100 * totalCompleted / planWorkouts.size();

        Optional<PlanWeek> weekOpt = Optional.ofNullable(currentPlanWeek);
        return new ActivePlanSummary(
                plan.getId(), plan.getTitle(), plan.getStatus(),
                currentWeek, plan.getDurationWeeks(),
                weekOpt.map(PlanWeek::getLabel).orElse(null),
                completionPercent, weekRemaining,
                weekOpt.map(PlanWeek::getTargetTss).orElse(null),
                weekActualTss);
    }

    public PlanProgress getProgress(String planId) {
        TrainingPlan plan = requirePlan(planId);
        return computeProgress(plan, scheduledWorkoutRepository.findByPlanId(planId));
    }

    private PlanProgress computeProgress(TrainingPlan plan, List<ScheduledWorkout> planWorkouts) {
        Map<ScheduleStatus, Long> counts = planWorkouts.stream()
                .collect(Collectors.groupingBy(ScheduledWorkout::getStatus, Collectors.counting()));

        int completed = counts.getOrDefault(ScheduleStatus.COMPLETED, 0L).intValue();
        int skipped = counts.getOrDefault(ScheduleStatus.SKIPPED, 0L).intValue();
        int pending = counts.getOrDefault(ScheduleStatus.PENDING, 0L).intValue();

        return PlanProgress.of(plan.getId(), completed + skipped + pending, completed, skipped, pending,
                computeCurrentWeek(plan.getStartDate()));
    }

    /**
     * Computes detailed analytics for a training plan including per-week
     * actual vs target TSS comparison and overall adherence.
     */
    public PlanAnalytics getAnalytics(String planId) {
        TrainingPlan plan = requirePlan(planId);
        return computeAnalytics(plan, scheduledWorkoutRepository.findByPlanId(planId));
    }

    private PlanAnalytics computeAnalytics(TrainingPlan plan, List<ScheduledWorkout> planWorkouts) {
        Map<String, Integer> swToWeek = new HashMap<>();
        for (PlanWeek week : plan.getWeeks()) {
            for (PlanDay day : week.getDays()) {
                for (String swId : day.getScheduledWorkoutIds()) {
                    swToWeek.put(swId, week.getWeekNumber());
                }
            }
        }

        Map<Integer, List<ScheduledWorkout>> byWeek = new HashMap<>();
        for (ScheduledWorkout sw : planWorkouts) {
            Integer weekNum = swToWeek.get(sw.getId());
            if (weekNum == null) weekNum = 0;
            byWeek.computeIfAbsent(weekNum, k -> new ArrayList<>()).add(sw);
        }

        int totalTargetTss = 0;
        int totalActualTss = 0;
        int totalCompleted = 0;
        int totalWorkouts = planWorkouts.size();

        List<PlanWeekAnalytics> weeklyBreakdown = new ArrayList<>();
        for (PlanWeek week : plan.getWeeks()) {
            List<ScheduledWorkout> weekWorkouts = byWeek.getOrDefault(week.getWeekNumber(), List.of());
            int weekActualTss = 0;
            int weekCompleted = 0;

            for (ScheduledWorkout sw : weekWorkouts) {
                if (sw.getStatus() == ScheduleStatus.COMPLETED && sw.getTss() != null) {
                    weekActualTss += sw.getTss();
                    weekCompleted++;
                } else if (sw.getStatus() == ScheduleStatus.COMPLETED) {
                    weekCompleted++;
                }
            }

            if (week.getTargetTss() != null) totalTargetTss += week.getTargetTss();
            totalActualTss += weekActualTss;
            totalCompleted += weekCompleted;

            weeklyBreakdown.add(PlanWeekAnalytics.of(
                    week.getWeekNumber(), week.getLabel(), week.getTargetTss(),
                    weekActualTss, weekCompleted, weekWorkouts.size()));
        }

        int currentWeek = computeCurrentWeek(plan.getStartDate());
        int completionPercent = totalWorkouts > 0 ? 100 * totalCompleted / totalWorkouts : 0;
        double adherence = totalTargetTss > 0 ? Math.min(100.0, 100.0 * totalActualTss / totalTargetTss) : 0.0;

        return new PlanAnalytics(plan.getId(), plan.getTitle(), plan.getStatus(),
                currentWeek, plan.getDurationWeeks(), completionPercent, adherence,
                totalTargetTss, totalActualTss, weeklyBreakdown);
    }

    private TrainingPlan requirePlan(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan", planId));
    }

    static int computeCurrentWeek(LocalDate startDate) {
        if (startDate == null) return 0;
        long daysSinceStart = LocalDate.now().toEpochDay() - startDate.toEpochDay();
        if (daysSinceStart < 0) return 0;
        return (int) (daysSinceStart / 7) + 1;
    }
}
