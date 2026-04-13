package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates future daily TSS from scheduled workouts and club sessions
 * to feed into the PMC forecast projection.
 */
@Service
public class ForecastTssService {

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubTrainingSessionRepository clubTrainingSessionRepository;

    public ForecastTssService(ScheduledWorkoutRepository scheduledWorkoutRepository,
                              TrainingRepository trainingRepository,
                              ClubMembershipRepository clubMembershipRepository,
                              ClubTrainingSessionRepository clubTrainingSessionRepository) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubTrainingSessionRepository = clubTrainingSessionRepository;
    }

    /**
     * Build a Map of date → (sport → TSS) for projected future days, aggregating
     * from ScheduledWorkouts and ClubTrainingSessions the user has joined.
     */
    public Map<LocalDate, Map<String, Double>> buildForecastTssMap(String userId,
                                                                    LocalDate from,
                                                                    LocalDate to) {
        Map<LocalDate, Map<String, Double>> result = new HashMap<>();

        // 1. Scheduled workouts (from coach assignments + plan activations)
        List<ScheduledWorkout> scheduledWorkouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, from, to)
                .stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING)
                .toList();

        // Batch-fetch trainings for TSS resolution
        Set<String> trainingIds = scheduledWorkouts.stream()
                .filter(sw -> sw.getTrainingId() != null)
                .map(ScheduledWorkout::getTrainingId)
                .collect(Collectors.toSet());

        // Also collect club session training IDs below
        List<String> userClubIds = clubMembershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .map(ClubMembership::getClubId)
                .toList();

        List<ClubTrainingSession> clubSessions = List.of();
        if (!userClubIds.isEmpty()) {
            clubSessions = clubTrainingSessionRepository
                    .findByClubIdInAndScheduledAtBetween(userClubIds,
                            from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(cs -> !Boolean.TRUE.equals(cs.getCancelled()))
                    .filter(cs -> cs.getParticipantIds().contains(userId))
                    .filter(cs -> cs.getLinkedTrainingId() != null)
                    .toList();

            clubSessions.stream()
                    .map(ClubTrainingSession::getLinkedTrainingId)
                    .forEach(trainingIds::add);
        }

        // Fetch all referenced trainings in one batch
        Map<String, Training> trainingMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, t -> t));

        // Process scheduled workouts
        for (ScheduledWorkout sw : scheduledWorkouts) {
            double tss = resolveTss(sw, trainingMap);
            if (tss <= 0) continue;
            String sport = resolveSport(sw, trainingMap);
            if ("SWIMMING".equals(sport)) continue; // Consistent with PMC exclusion
            result.computeIfAbsent(sw.getScheduledDate(), k -> new HashMap<>())
                    .merge(sport, tss, Double::sum);
        }

        // Process club sessions
        for (ClubTrainingSession cs : clubSessions) {
            Training training = trainingMap.get(cs.getLinkedTrainingId());
            if (training == null || training.getEstimatedTss() == null || training.getEstimatedTss() <= 0)
                continue;
            String sport = training.getSportType() != null ? training.getSportType().name() : "CYCLING";
            if ("SWIMMING".equals(sport)) continue;
            LocalDate date = cs.getScheduledAt().toLocalDate();
            result.computeIfAbsent(date, k -> new HashMap<>())
                    .merge(sport, training.getEstimatedTss().doubleValue(), Double::sum);
        }

        return result;
    }

    private double resolveTss(ScheduledWorkout sw, Map<String, Training> trainingMap) {
        if (sw.getTss() != null && sw.getTss() > 0) return sw.getTss();
        if (sw.getTrainingId() == null) return 0;
        Training t = trainingMap.get(sw.getTrainingId());
        return (t != null && t.getEstimatedTss() != null) ? t.getEstimatedTss() : 0;
    }

    private String resolveSport(ScheduledWorkout sw, Map<String, Training> trainingMap) {
        if (sw.getTrainingId() == null) return "CYCLING";
        Training t = trainingMap.get(sw.getTrainingId());
        return (t != null && t.getSportType() != null) ? t.getSportType().name() : "CYCLING";
    }
}
