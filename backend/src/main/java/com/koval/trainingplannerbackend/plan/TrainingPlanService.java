package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
public class TrainingPlanService {

    private final TrainingPlanRepository planRepository;
    private final TrainingRepository trainingRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final NotificationService notificationService;
    private final ReceivedTrainingService receivedTrainingService;

    public TrainingPlanService(TrainingPlanRepository planRepository,
                               TrainingRepository trainingRepository,
                               ScheduledWorkoutRepository scheduledWorkoutRepository,
                               NotificationService notificationService,
                               ReceivedTrainingService receivedTrainingService) {
        this.planRepository = planRepository;
        this.trainingRepository = trainingRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
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
        List<TrainingPlan> created = planRepository.findByCreatedByOrderByCreatedAtDesc(userId);
        List<TrainingPlan> assigned = planRepository.findByAthleteIdsContaining(userId);
        // 'created' wins on conflict (keeps the earlier, more authoritative copy).
        return Stream.concat(created.stream(), assigned.stream())
                .collect(Collectors.toMap(TrainingPlan::getId, p -> p, (first, second) -> first, LinkedHashMap::new))
                .values().stream().toList();
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
                        .filter(day -> day.getScheduledWorkoutId() != null)
                        .map(day -> Map.entry(day.getScheduledWorkoutId(), week.getWeekNumber())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

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

        Optional<PlanWeek> weekOpt = Optional.ofNullable(currentPlanWeek);
        return new ActivePlanSummary(
                plan.getId(), plan.getTitle(), plan.getStatus(),
                currentWeek, plan.getDurationWeeks(),
                weekOpt.map(PlanWeek::getLabel).orElse(null),
                completionPercent, weekRemaining,
                weekOpt.map(PlanWeek::getTargetTss).orElse(null),
                weekActualTss);
    }

    @Transactional
    public TrainingPlan updatePlan(String planId, TrainingPlan updates, String userId) {
        TrainingPlan existing = getPlan(planId);
        verifyOwnership(existing, userId);

        boolean weeksChanged = updates.getWeeks() != null && !updates.getWeeks().isEmpty();

        if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getSportType() != null) existing.setSportType(updates.getSportType());
        if (updates.getStartDate() != null) existing.setStartDate(updates.getStartDate());
        if (updates.getDurationWeeks() > 0) existing.setDurationWeeks(updates.getDurationWeeks());
        if (weeksChanged) existing.setWeeks(updates.getWeeks());
        if (updates.getGoalRaceId() != null) existing.setGoalRaceId(updates.getGoalRaceId());
        if (updates.getTargetFtp() != null) existing.setTargetFtp(updates.getTargetFtp());
        if (updates.getAthleteIds() != null) existing.setAthleteIds(updates.getAthleteIds());

        // For active plans, propagate day-level edits to the calendar by
        // rebuilding future PENDING scheduled workouts from the new structure.
        if (weeksChanged && existing.getStatus() == PlanStatus.ACTIVE && existing.getStartDate() != null) {
            syncFutureScheduledWorkouts(existing, userId);
        }

        return planRepository.save(existing);
    }

    private void syncFutureScheduledWorkouts(TrainingPlan plan, String userId) {
        LocalDate today = LocalDate.now();

        // Delete future PENDING scheduled workouts; preserve past, completed, skipped.
        List<String> futurePendingIds = scheduledWorkoutRepository.findByPlanId(plan.getId()).stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING
                        && !sw.getScheduledDate().isBefore(today))
                .map(ScheduledWorkout::getId)
                .toList();
        scheduledWorkoutRepository.deleteAllById(futurePendingIds);

        // Clear stale scheduledWorkoutId references on future days so we re-link below.
        plan.getWeeks().forEach(week -> week.getDays().forEach(day -> {
            LocalDate date = computeDate(plan.getStartDate(), week.getWeekNumber(), day.getDayOfWeek());
            if (!date.isBefore(today)) day.setScheduledWorkoutId(null);
        }));

        List<String> targetAthleteIds = plan.getAthleteIds().isEmpty()
                ? List.of(userId)
                : plan.getAthleteIds();

        buildAndPersistScheduledWorkouts(plan, targetAthleteIds, userId, today, fetchTrainingsForPlan(plan));
    }

    /** Fetches all distinct trainings referenced by a plan in one query. */
    private Map<String, Training> fetchTrainingsForPlan(TrainingPlan plan) {
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
     * Builds {@link ScheduledWorkout}s for every (week, day, athlete) combination scheduled on or
     * after {@code cutoff}, persists them in one batch, and links the saved IDs back onto each
     * {@link PlanDay}. When multiple athletes share a day, the last athlete's id wins on the day —
     * matching the previous imperative behaviour.
     */
    private void buildAndPersistScheduledWorkouts(TrainingPlan plan, List<String> athleteIds,
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

    public TrainingPlan activatePlan(String planId, String userId, LocalDate startDate) {
        return activatePlan(planId, userId, startDate, null);
    }

    /**
     * Activates a plan, creating scheduled workouts in the calendar.
     * If {@code startDate} is provided, it overrides any existing start date on the plan,
     * enabling plans to be reused as templates with different start dates.
     * If {@code newAthleteIds} is provided and non-empty, it replaces the plan's athleteIds.
     */
    @Transactional
    public TrainingPlan activatePlan(String planId, String userId, LocalDate startDate, List<String> newAthleteIds) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);

        if (plan.getStatus() != PlanStatus.DRAFT && plan.getStatus() != PlanStatus.PAUSED) {
            throw new ForbiddenOperationException("Plan must be in DRAFT or PAUSED status to activate");
        }

        // Apply start date if provided, otherwise use existing
        if (startDate != null) {
            plan.setStartDate(startDate);
        }
        // Apply athlete IDs override if provided
        if (newAthleteIds != null && !newAthleteIds.isEmpty()) {
            plan.setAthleteIds(newAthleteIds);
        }
        if (plan.getStartDate() == null) {
            throw new ForbiddenOperationException("A start date is required to activate the plan");
        }

        List<String> targetAthleteIds = plan.getAthleteIds().isEmpty()
                ? List.of(userId) // self-assigned plan
                : plan.getAthleteIds();

        Map<String, Training> trainingById = fetchTrainingsForPlan(plan);
        buildAndPersistScheduledWorkouts(plan, targetAthleteIds, userId, LocalDate.now(), trainingById);

        plan.setStatus(PlanStatus.ACTIVE);
        plan.setActivatedAt(LocalDateTime.now());
        TrainingPlan savedPlan = planRepository.save(plan);

        // Create ReceivedTraining entries for coach-assigned plans
        if (!plan.getAthleteIds().isEmpty()) {
            trainingById.keySet().forEach(trainingId ->
                    receivedTrainingService.createReceivedTrainings(trainingId, plan.getAthleteIds(),
                            userId, ReceivedTrainingOrigin.PLAN, planId, plan.getTitle()));
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

        LocalDate today = LocalDate.now();

        // Cancel future pending scheduled workouts for this plan
        List<String> futureIds = scheduledWorkoutRepository.findByPlanId(planId).stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING
                        && sw.getScheduledDate().isAfter(today))
                .map(ScheduledWorkout::getId)
                .toList();
        scheduledWorkoutRepository.deleteAllById(futureIds);

        // Clear scheduledWorkoutIds for cancelled days
        plan.getWeeks().forEach(week -> week.getDays().stream()
                .filter(day -> day.getScheduledWorkoutId() != null)
                .filter(day -> computeDate(plan.getStartDate(), week.getWeekNumber(), day.getDayOfWeek()).isAfter(today))
                .forEach(day -> day.setScheduledWorkoutId(null)));

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
        Map<ScheduleStatus, Long> counts = scheduledWorkoutRepository.findByPlanId(planId).stream()
                .collect(Collectors.groupingBy(ScheduledWorkout::getStatus, Collectors.counting()));

        int completed = counts.getOrDefault(ScheduleStatus.COMPLETED, 0L).intValue();
        int skipped = counts.getOrDefault(ScheduleStatus.SKIPPED, 0L).intValue();
        int pending = counts.getOrDefault(ScheduleStatus.PENDING, 0L).intValue();

        return PlanProgress.of(planId, completed + skipped + pending, completed, skipped, pending,
                computeCurrentWeek(plan.getStartDate()));
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

    /**
     * Resume a paused plan back to ACTIVE without recreating any scheduled workouts.
     * Use {@link #activatePlan(String, String, java.time.LocalDate)} if you also need
     * to repopulate the calendar with future days.
     */
    @Transactional
    public TrainingPlan resumePlan(String planId, String userId) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);
        if (plan.getStatus() != PlanStatus.PAUSED) {
            throw new ForbiddenOperationException("Only paused plans can be resumed");
        }
        plan.setStatus(PlanStatus.ACTIVE);
        return planRepository.save(plan);
    }

    /**
     * Clone an existing plan as a new DRAFT, copying its title, weeks structure and
     * sport but giving it a new title and clearing activation state. Useful for
     * reusing a plan as a template.
     */
    @Transactional
    public TrainingPlan clonePlan(String planId, String newTitle, LocalDate newStartDate, String userId) {
        TrainingPlan source = getPlan(planId);
        TrainingPlan copy = new TrainingPlan();
        copy.setTitle(newTitle != null && !newTitle.isBlank() ? newTitle : source.getTitle() + " (copy)");
        copy.setDescription(source.getDescription());
        copy.setSportType(source.getSportType());
        copy.setStartDate(newStartDate);
        copy.setDurationWeeks(source.getDurationWeeks());
        copy.setTargetFtp(source.getTargetFtp());
        copy.setGoalRaceId(source.getGoalRaceId());

        List<PlanWeek> weeksCopy = new ArrayList<>();
        for (PlanWeek w : source.getWeeks()) {
            PlanWeek wc = new PlanWeek();
            wc.setWeekNumber(w.getWeekNumber());
            wc.setLabel(w.getLabel());
            wc.setTargetTss(w.getTargetTss());
            List<PlanDay> daysCopy = new ArrayList<>();
            for (PlanDay d : w.getDays()) {
                PlanDay dc = new PlanDay();
                dc.setDayOfWeek(d.getDayOfWeek());
                dc.setTrainingId(d.getTrainingId());
                dc.setNotes(d.getNotes());
                daysCopy.add(dc);
            }
            wc.setDays(daysCopy);
            weeksCopy.add(wc);
        }
        copy.setWeeks(weeksCopy);
        return createPlan(copy, userId);
    }

    public void deletePlan(String planId, String userId) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);

        // Delete associated scheduled workouts if plan was active
        if (plan.getStatus() == PlanStatus.ACTIVE) {
            List<String> pendingIds = scheduledWorkoutRepository.findByPlanId(planId).stream()
                    .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING)
                    .map(ScheduledWorkout::getId)
                    .toList();
            scheduledWorkoutRepository.deleteAllById(pendingIds);
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
