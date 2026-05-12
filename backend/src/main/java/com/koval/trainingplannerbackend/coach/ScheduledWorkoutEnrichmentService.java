package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.plan.PlanDay;
import com.koval.trainingplannerbackend.plan.PlanWeek;
import com.koval.trainingplannerbackend.plan.TrainingPlan;
import com.koval.trainingplannerbackend.plan.TrainingPlanRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enriches scheduled workouts with training and plan-week metadata, and merges
 * a member's club-training participations into a unified schedule view.
 *
 * <p>Kept separate from {@link ScheduleService} so the write-path service is not
 * coupled to plan/club lookup logic that is purely read-oriented.
 */
@Service
public class ScheduledWorkoutEnrichmentService {

    /**
     * Max date window for {@link #getUnifiedSchedule(List, String, LocalDate, LocalDate)}.
     * Multi-club athletes can otherwise pull MB-scale JSON if the caller asks for an
     * unbounded range. Calendar / weekly views never need more than this.
     */
    private static final long MAX_SCHEDULE_WINDOW_DAYS = 180;

    private final TrainingRepository trainingRepository;
    private final TrainingPlanRepository planRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubTrainingSessionRepository clubTrainingSessionRepository;
    private final ClubRepository clubRepository;
    private final ClubGroupRepository clubGroupRepository;

    public ScheduledWorkoutEnrichmentService(TrainingRepository trainingRepository,
                                             TrainingPlanRepository planRepository,
                                             ClubMembershipRepository clubMembershipRepository,
                                             ClubTrainingSessionRepository clubTrainingSessionRepository,
                                             ClubRepository clubRepository,
                                             ClubGroupRepository clubGroupRepository) {
        this.trainingRepository = trainingRepository;
        this.planRepository = planRepository;
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubTrainingSessionRepository = clubTrainingSessionRepository;
        this.clubRepository = clubRepository;
        this.clubGroupRepository = clubGroupRepository;
    }

    /**
     * Enrich a list of scheduled workouts with training metadata (title, type,
     * duration, sport, estimated TSS/IF) and plan context (plan title, week number,
     * week label) when the workout belongs to a training plan.
     */
    public List<ScheduledWorkoutResponse> enrichList(List<ScheduledWorkout> workouts) {
        if (workouts.isEmpty()) return List.of();

        List<String> trainingIds = workouts.stream()
                .map(ScheduledWorkout::getTrainingId)
                .distinct()
                .toList();

        Map<String, Training> trainingsMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Function.identity()));

        Map<String, PlanWeekInfo> planWeekIndex = buildPlanWeekIndex(workouts);

        return workouts.stream()
                .map(sw -> buildResponse(sw,
                        Optional.ofNullable(trainingsMap.get(sw.getTrainingId())),
                        Optional.ofNullable(planWeekIndex.get(sw.getId()))))
                .toList();
    }

    /**
     * Enrich a single scheduled workout with training metadata and plan context.
     */
    public ScheduledWorkoutResponse enrichSingle(ScheduledWorkout sw) {
        Optional<Training> tOpt = Optional.ofNullable(sw.getTrainingId()).flatMap(trainingRepository::findById);
        Optional<PlanWeekInfo> pOpt = Optional.ofNullable(sw.getPlanId())
                .map(planId -> resolvePlanWeekInfo(planId, sw.getId()));
        return buildResponse(sw, tOpt, pOpt);
    }

    /**
     * Build a unified schedule merging regular assigned workouts with club training
     * sessions where the athlete is a participant.
     */
    public List<ScheduledWorkoutResponse> getUnifiedSchedule(
            List<ScheduledWorkout> workouts, String athleteId,
            LocalDate start, LocalDate end) {

        if (start == null || end == null || end.isBefore(start)) {
            throw new ValidationException("start and end are required and end must be on or after start", "INVALID_SCHEDULE_RANGE");
        }
        long windowDays = end.toEpochDay() - start.toEpochDay() + 1;
        if (windowDays > MAX_SCHEDULE_WINDOW_DAYS) {
            throw new ValidationException(
                    "Schedule window cannot exceed " + MAX_SCHEDULE_WINDOW_DAYS + " days (requested " + windowDays + ")",
                    "SCHEDULE_RANGE_TOO_LARGE");
        }

        List<ScheduledWorkoutResponse> result = new ArrayList<>(enrichList(workouts));

        List<ClubMembership> memberships = clubMembershipRepository.findByUserId(athleteId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .toList();
        if (memberships.isEmpty()) {
            return sortByScheduledDate(result);
        }

        List<String> clubIds = memberships.stream().map(ClubMembership::getClubId).toList();
        Map<String, Club> clubMap = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));

        List<ClubTrainingSession> sessions = clubTrainingSessionRepository
                .findByClubIdInAndScheduledAtBetween(clubIds, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        Set<String> athleteGroupIds = clubGroupRepository
                .findByClubIdInAndMemberIdsContaining(clubIds, athleteId).stream()
                .map(g -> g.getId())
                .collect(Collectors.toSet());

        List<ClubTrainingSession> relevantSessions = sessions.stream()
                .filter(s -> s.getParticipantIds().contains(athleteId))
                .filter(s -> {
                    if (s.getClubGroupId() != null && !s.getClubGroupId().isBlank()) {
                        return athleteGroupIds.contains(s.getClubGroupId());
                    }
                    return true;
                })
                .toList();

        if (relevantSessions.isEmpty()) {
            return sortByScheduledDate(result);
        }

        List<String> linkedTrainingIds = relevantSessions.stream()
                .map(ClubTrainingSession::getLinkedTrainingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Training> linkedTrainings = linkedTrainingIds.isEmpty() ? Map.of()
                : trainingRepository.findAllById(linkedTrainingIds).stream()
                        .collect(Collectors.toMap(Training::getId, Function.identity()));

        Map<String, String> groupNameMap = clubGroupRepository.findByClubIdIn(clubIds).stream()
                .collect(Collectors.toMap(g -> g.getId(), g -> g.getName()));

        for (ClubTrainingSession s : relevantSessions) {
            Club club = clubMap.get(s.getClubId());
            String clubName = club != null ? club.getName() : null;
            String clubGroupName = s.getClubGroupId() != null ? groupNameMap.get(s.getClubGroupId()) : null;
            Training linked = s.getLinkedTrainingId() != null ? linkedTrainings.get(s.getLinkedTrainingId()) : null;
            result.add(ScheduledWorkoutResponse.fromClubSession(s, clubName, clubGroupName, linked));
        }

        return sortByScheduledDate(result);
    }

    private static List<ScheduledWorkoutResponse> sortByScheduledDate(List<ScheduledWorkoutResponse> list) {
        list.sort(Comparator.comparing(ScheduledWorkoutResponse::scheduledDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    private static ScheduledWorkoutResponse buildResponse(ScheduledWorkout sw,
                                                          Optional<Training> tOpt,
                                                          Optional<PlanWeekInfo> pOpt) {
        return ScheduledWorkoutResponse.from(sw,
                tOpt.map(Training::getTitle).orElse(null),
                tOpt.map(Training::getTrainingType).orElse(null),
                tOpt.map(Training::getEstimatedDurationSeconds).orElse(null),
                tOpt.map(Training::getSportType).orElse(null),
                tOpt.map(Training::getEstimatedTss).orElse(null),
                tOpt.map(Training::getEstimatedIf).orElse(null),
                pOpt.map(PlanWeekInfo::planId).orElse(null),
                pOpt.map(PlanWeekInfo::planTitle).orElse(null),
                pOpt.map(PlanWeekInfo::weekNumber).orElse(null),
                pOpt.map(PlanWeekInfo::weekLabel).orElse(null));
    }

    /** Lightweight holder for plan context associated with a scheduled workout. */
    private record PlanWeekInfo(String planId, String planTitle, int weekNumber, String weekLabel) {}

    private Map<String, PlanWeekInfo> buildPlanWeekIndex(List<ScheduledWorkout> workouts) {
        List<String> planIds = workouts.stream()
                .map(ScheduledWorkout::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (planIds.isEmpty()) return Map.of();

        Map<String, TrainingPlan> plansMap = planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(TrainingPlan::getId, Function.identity()));

        Map<String, PlanWeekInfo> index = new HashMap<>();
        for (TrainingPlan plan : plansMap.values()) {
            for (PlanWeek week : plan.getWeeks()) {
                for (PlanDay day : week.getDays()) {
                    for (String swId : day.getScheduledWorkoutIds()) {
                        index.put(swId,
                                new PlanWeekInfo(plan.getId(), plan.getTitle(), week.getWeekNumber(), week.getLabel()));
                    }
                }
            }
        }
        return index;
    }

    private PlanWeekInfo resolvePlanWeekInfo(String planId, String scheduledWorkoutId) {
        return planRepository.findById(planId)
                .map(plan -> {
                    for (PlanWeek week : plan.getWeeks()) {
                        for (PlanDay day : week.getDays()) {
                            if (day.getScheduledWorkoutIds().contains(scheduledWorkoutId)) {
                                return new PlanWeekInfo(plan.getId(), plan.getTitle(), week.getWeekNumber(), week.getLabel());
                            }
                        }
                    }
                    return new PlanWeekInfo(plan.getId(), plan.getTitle(), 0, null);
                })
                .orElse(null);
    }
}
