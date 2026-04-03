package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingOrigin;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class TrainingPlanService {

    private final TrainingPlanRepository planRepository;
    private final TrainingRepository trainingRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingService trainingService;
    private final NotificationService notificationService;
    private final ReceivedTrainingService receivedTrainingService;

    public TrainingPlanService(TrainingPlanRepository planRepository,
                               TrainingRepository trainingRepository,
                               ScheduledWorkoutRepository scheduledWorkoutRepository,
                               TrainingService trainingService,
                               NotificationService notificationService,
                               ReceivedTrainingService receivedTrainingService) {
        this.planRepository = planRepository;
        this.trainingRepository = trainingRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingService = trainingService;
        this.notificationService = notificationService;
        this.receivedTrainingService = receivedTrainingService;
    }

    public TrainingPlan createPlan(TrainingPlan plan, String userId) {
        plan.setCreatedBy(userId);
        plan.setStatus(PlanStatus.DRAFT);
        plan.setCreatedAt(LocalDateTime.now());
        return planRepository.save(plan);
    }

    public TrainingPlan getPlan(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan", planId));
    }

    public List<TrainingPlan> listPlans(String userId) {
        return planRepository.findByCreatedByOrderByCreatedAtDesc(userId);
    }

    public List<TrainingPlan> listPlansByAthlete(String athleteId) {
        return planRepository.findByAthleteIdsContaining(athleteId);
    }

    /**
     * Returns plan summaries for an athlete including progress and analytics,
     * used by the coach dashboard.
     */
    public List<AthletePlanSummary> listPlansByAthleteWithProgress(String athleteId) {
        List<TrainingPlan> plans = planRepository.findByAthleteIdsContaining(athleteId);
        return plans.stream()
                .map(plan -> {
                    int currentWeek = computeCurrentWeek(plan.getStartDate());
                    PlanProgress progress = getProgress(plan.getId());
                    PlanAnalytics analytics = getAnalytics(plan.getId());
                    return AthletePlanSummary.from(plan, currentWeek, progress, analytics);
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
                .orElse(null);

        // Also check plans assigned to the user as athlete
        if (plan == null) {
            plan = planRepository.findByAthleteIdsContaining(userId).stream()
                    .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);
        }

        if (plan == null) return null;

        int currentWeek = computeCurrentWeek(plan.getStartDate());
        List<ScheduledWorkout> planWorkouts = scheduledWorkoutRepository.findByPlanId(plan.getId());

        // Build scheduledWorkoutId → weekNumber index
        Map<String, Integer> swToWeek = new HashMap<>();
        for (PlanWeek week : plan.getWeeks()) {
            for (PlanDay day : week.getDays()) {
                if (day.getScheduledWorkoutId() != null) {
                    swToWeek.put(day.getScheduledWorkoutId(), week.getWeekNumber());
                }
            }
        }

        // Compute current week stats
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

        return new ActivePlanSummary(
                plan.getId(), plan.getTitle(), plan.getStatus(),
                currentWeek, plan.getDurationWeeks(),
                currentPlanWeek != null ? currentPlanWeek.getLabel() : null,
                completionPercent, weekRemaining,
                currentPlanWeek != null ? currentPlanWeek.getTargetTss() : null,
                weekActualTss);
    }

    public TrainingPlan updatePlan(String planId, TrainingPlan updates, String userId) {
        TrainingPlan existing = getPlan(planId);
        verifyOwnership(existing, userId);

        if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getSportType() != null) existing.setSportType(updates.getSportType());
        if (updates.getStartDate() != null) existing.setStartDate(updates.getStartDate());
        if (updates.getDurationWeeks() > 0) existing.setDurationWeeks(updates.getDurationWeeks());
        if (updates.getWeeks() != null && !updates.getWeeks().isEmpty()) existing.setWeeks(updates.getWeeks());
        if (updates.getGoalRaceId() != null) existing.setGoalRaceId(updates.getGoalRaceId());
        if (updates.getTargetFtp() != null) existing.setTargetFtp(updates.getTargetFtp());
        if (updates.getAthleteIds() != null) existing.setAthleteIds(updates.getAthleteIds());

        return planRepository.save(existing);
    }

    /**
     * Activates a plan, creating scheduled workouts in the calendar.
     * If {@code startDate} is provided, it overrides any existing start date on the plan,
     * enabling plans to be reused as templates with different start dates.
     */
    @Transactional
    public TrainingPlan activatePlan(String planId, String userId, LocalDate startDate) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);

        if (plan.getStatus() != PlanStatus.DRAFT && plan.getStatus() != PlanStatus.PAUSED) {
            throw new ForbiddenOperationException("Plan must be in DRAFT or PAUSED status to activate");
        }

        // Apply start date if provided, otherwise use existing
        if (startDate != null) {
            plan.setStartDate(startDate);
        }
        if (plan.getStartDate() == null) {
            throw new ForbiddenOperationException("A start date is required to activate the plan");
        }

        List<String> targetAthleteIds = plan.getAthleteIds().isEmpty()
                ? List.of(userId) // self-assigned plan
                : plan.getAthleteIds();

        for (PlanWeek week : plan.getWeeks()) {
            for (PlanDay day : week.getDays()) {
                if (day.getTrainingId() == null) continue;

                LocalDate date = computeDate(plan.getStartDate(), week.getWeekNumber(), day.getDayOfWeek());
                if (date.isBefore(LocalDate.now())) continue; // skip past dates

                for (String athleteId : targetAthleteIds) {
                    ScheduledWorkout sw = new ScheduledWorkout();
                    sw.setTrainingId(day.getTrainingId());
                    sw.setAthleteId(athleteId);
                    sw.setAssignedBy(userId);
                    sw.setScheduledDate(date);
                    sw.setNotes(day.getNotes());
                    sw.setPlanId(planId);
                    sw.setStatus(ScheduleStatus.PENDING);

                    // Enrich with TSS from training
                    try {
                        Training training = trainingService.getTrainingById(day.getTrainingId());
                        if (training != null && training.getEstimatedTss() != null) {
                            sw.setTss(training.getEstimatedTss());
                            sw.setIntensityFactor(training.getEstimatedIf());
                        }
                    } catch (Exception ignored) {}

                    ScheduledWorkout saved = scheduledWorkoutRepository.save(sw);
                    day.setScheduledWorkoutId(saved.getId());
                }
            }
        }

        plan.setStatus(PlanStatus.ACTIVE);
        plan.setActivatedAt(LocalDateTime.now());
        TrainingPlan savedPlan = planRepository.save(plan);

        // Create ReceivedTraining entries for coach-assigned plans
        if (!plan.getAthleteIds().isEmpty()) {
            List<String> uniqueTrainingIds = plan.getWeeks().stream()
                    .flatMap(w -> w.getDays().stream())
                    .map(PlanDay::getTrainingId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();

            for (String trainingId : uniqueTrainingIds) {
                receivedTrainingService.createReceivedTrainings(trainingId, plan.getAthleteIds(),
                        userId, ReceivedTrainingOrigin.PLAN, planId, plan.getTitle());
            }
        }

        // Notify athletes if coach-assigned
        if (!plan.getAthleteIds().isEmpty()) {
            notificationService.sendToUsers(
                    plan.getAthleteIds(),
                    "New Training Plan",
                    "You've been assigned: " + plan.getTitle(),
                    Map.of("type", "PLAN_ACTIVATED", "planId", planId));
        }

        return savedPlan;
    }

    @Transactional
    public TrainingPlan pausePlan(String planId, String userId) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);

        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new ForbiddenOperationException("Only active plans can be paused");
        }

        // Cancel future pending scheduled workouts for this plan
        List<ScheduledWorkout> planWorkouts = scheduledWorkoutRepository.findByPlanId(planId);
        for (ScheduledWorkout sw : planWorkouts) {
            if (sw.getStatus() == ScheduleStatus.PENDING
                    && sw.getScheduledDate().isAfter(LocalDate.now())) {
                scheduledWorkoutRepository.deleteById(sw.getId());
            }
        }

        // Clear scheduledWorkoutIds for cancelled days
        for (PlanWeek week : plan.getWeeks()) {
            for (PlanDay day : week.getDays()) {
                if (day.getScheduledWorkoutId() != null) {
                    LocalDate date = computeDate(plan.getStartDate(), week.getWeekNumber(), day.getDayOfWeek());
                    if (date.isAfter(LocalDate.now())) {
                        day.setScheduledWorkoutId(null);
                    }
                }
            }
        }

        plan.setStatus(PlanStatus.PAUSED);
        return planRepository.save(plan);
    }

    @Transactional
    public TrainingPlan completePlan(String planId, String userId) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);

        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new ForbiddenOperationException("Only active plans can be completed");
        }

        plan.setStatus(PlanStatus.COMPLETED);
        return planRepository.save(plan);
    }

    public PlanProgress getProgress(String planId) {
        TrainingPlan plan = getPlan(planId);
        List<ScheduledWorkout> planWorkouts = scheduledWorkoutRepository.findByPlanId(planId);

        int completed = 0, skipped = 0, pending = 0;
        for (ScheduledWorkout sw : planWorkouts) {
            switch (sw.getStatus()) {
                case COMPLETED -> completed++;
                case SKIPPED -> skipped++;
                case PENDING -> pending++;
            }
        }

        int total = completed + skipped + pending;
        int currentWeek = computeCurrentWeek(plan.getStartDate());

        return PlanProgress.of(planId, total, completed, skipped, pending, currentWeek);
    }

    /**
     * Computes detailed analytics for a training plan including per-week
     * actual vs target TSS comparison and overall adherence.
     */
    public PlanAnalytics getAnalytics(String planId) {
        TrainingPlan plan = getPlan(planId);
        List<ScheduledWorkout> planWorkouts = scheduledWorkoutRepository.findByPlanId(planId);

        // Map scheduledWorkoutId → weekNumber from plan structure
        Map<String, Integer> swToWeek = new HashMap<>();
        for (PlanWeek week : plan.getWeeks()) {
            for (PlanDay day : week.getDays()) {
                if (day.getScheduledWorkoutId() != null) {
                    swToWeek.put(day.getScheduledWorkoutId(), week.getWeekNumber());
                }
            }
        }

        // Group workouts by week number
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

        return new PlanAnalytics(planId, plan.getTitle(), plan.getStatus(),
                currentWeek, plan.getDurationWeeks(), completionPercent, adherence,
                totalTargetTss, totalActualTss, weeklyBreakdown);
    }

    public void deletePlan(String planId, String userId) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);

        // Delete associated scheduled workouts if plan was active
        if (plan.getStatus() == PlanStatus.ACTIVE) {
            List<ScheduledWorkout> planWorkouts = scheduledWorkoutRepository.findByPlanId(planId);
            for (ScheduledWorkout sw : planWorkouts) {
                if (sw.getStatus() == ScheduleStatus.PENDING) {
                    scheduledWorkoutRepository.deleteById(sw.getId());
                }
            }
        }

        planRepository.deleteById(planId);
    }

    /**
     * Returns a populated view of the plan with Training objects resolved from their IDs.
     */
    public TrainingPlanPopulated populatePlan(String planId) {
        TrainingPlan plan = getPlan(planId);
        List<String> trainingIds = TrainingPlanPopulated.collectTrainingIds(plan);

        Map<String, Training> trainingsById = StreamSupport
                .stream(trainingRepository.findAllById(trainingIds).spliterator(), false)
                .collect(Collectors.toMap(Training::getId, t -> t, (a, b) -> a));

        return TrainingPlanPopulated.from(plan, trainingsById);
    }

    private void verifyOwnership(TrainingPlan plan, String userId) {
        if (!plan.getCreatedBy().equals(userId)) {
            throw new ForbiddenOperationException("You do not own this training plan");
        }
    }

    private LocalDate computeDate(LocalDate planStartDate, int weekNumber, DayOfWeek dayOfWeek) {
        // planStartDate is the Monday of week 1
        LocalDate weekStart = planStartDate.plusWeeks(weekNumber - 1);
        // Adjust weekStart to the Monday of that week
        LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.plusDays(dayOfWeek.getValue() - 1);
    }

    private int computeCurrentWeek(LocalDate startDate) {
        if (startDate == null) return 0;
        long daysSinceStart = LocalDate.now().toEpochDay() - startDate.toEpochDay();
        if (daysSinceStart < 0) return 0;
        return (int) (daysSinceStart / 7) + 1;
    }
}
