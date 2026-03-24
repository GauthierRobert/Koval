package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TrainingPlanService {

    private final TrainingPlanRepository planRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingService trainingService;
    private final NotificationService notificationService;

    public TrainingPlanService(TrainingPlanRepository planRepository,
                               ScheduledWorkoutRepository scheduledWorkoutRepository,
                               TrainingService trainingService,
                               NotificationService notificationService) {
        this.planRepository = planRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingService = trainingService;
        this.notificationService = notificationService;
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

    @Transactional
    public TrainingPlan activatePlan(String planId, String userId) {
        TrainingPlan plan = getPlan(planId);
        verifyOwnership(plan, userId);

        if (plan.getStatus() != PlanStatus.DRAFT && plan.getStatus() != PlanStatus.PAUSED) {
            throw new ForbiddenOperationException("Plan must be in DRAFT or PAUSED status to activate");
        }
        if (plan.getStartDate() == null) {
            throw new ForbiddenOperationException("Plan must have a start date before activation");
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
