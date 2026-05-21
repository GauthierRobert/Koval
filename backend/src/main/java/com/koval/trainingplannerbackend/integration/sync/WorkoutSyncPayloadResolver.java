package com.koval.trainingplannerbackend.integration.sync;

import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.club.session.GroupLinkedTraining;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves a {@link WorkoutSyncPayload} from event coordinates. Centralizing this lookup
 * keeps providers stateless and lets us evolve the schedule/club session models without
 * touching each integration.
 *
 * <p>For {@link WorkoutSyncSourceType#CLUB_SESSION}, the {@code sourceId} is a composite of
 * {@code "<sessionId>:<athleteId>"} so a single record exists per athlete per session and the
 * training resolution honours which club-group the athlete belongs to.
 */
@Component
public class WorkoutSyncPayloadResolver {

    public static final String CLUB_SESSION_ID_SEPARATOR = ":";

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final ClubTrainingSessionRepository clubSessionRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final TrainingRepository trainingRepository;

    public WorkoutSyncPayloadResolver(ScheduledWorkoutRepository scheduledWorkoutRepository,
                                      ClubTrainingSessionRepository clubSessionRepository,
                                      ClubGroupRepository clubGroupRepository,
                                      TrainingRepository trainingRepository) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.clubSessionRepository = clubSessionRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.trainingRepository = trainingRepository;
    }

    /** Build {@code "<sessionId>:<athleteId>"} for use as {@code sourceId} on CLUB_SESSION events. */
    public static String clubSessionSourceId(String sessionId, String athleteId) {
        return sessionId + CLUB_SESSION_ID_SEPARATOR + athleteId;
    }

    public Optional<WorkoutSyncPayload> resolve(String athleteId, WorkoutSyncSourceType type, String sourceId) {
        return switch (type) {
            case SCHEDULED_WORKOUT -> resolveScheduled(athleteId, sourceId);
            case CLUB_SESSION -> resolveClubSession(athleteId, sourceId);
        };
    }

    private Optional<WorkoutSyncPayload> resolveScheduled(String athleteId, String scheduledWorkoutId) {
        return scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .flatMap(sw -> trainingRepository.findById(sw.getTrainingId())
                        .map(t -> toPayload(athleteId, WorkoutSyncSourceType.SCHEDULED_WORKOUT,
                                scheduledWorkoutId, t, sw.getScheduledDate(), t.getTitle(), sw.getNotes())));
    }

    private Optional<WorkoutSyncPayload> resolveClubSession(String athleteId, String sourceId) {
        String[] parts = sourceId.split(CLUB_SESSION_ID_SEPARATOR, 2);
        if (parts.length != 2) return Optional.empty();
        String sessionId = parts[0];

        ClubTrainingSession session = clubSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getScheduledAt() == null) return Optional.empty();
        if (Boolean.TRUE.equals(session.getCancelled())) return Optional.empty();

        String trainingId = resolveLinkedTrainingId(session, athleteId);
        if (trainingId == null) return Optional.empty();

        Training training = trainingRepository.findById(trainingId).orElse(null);
        if (training == null) return Optional.empty();

        LocalDate date = session.getScheduledAt().toLocalDate();
        String title = session.getTitle() != null ? session.getTitle() : training.getTitle();
        return Optional.of(toPayload(athleteId, WorkoutSyncSourceType.CLUB_SESSION, sourceId,
                training, date, title, session.getDescription()));
    }

    /** Pick the linked training that matches the athlete's club group, else the club-level one. */
    private String resolveLinkedTrainingId(ClubTrainingSession session, String athleteId) {
        var effective = session.getEffectiveLinkedTrainings();
        if (effective.isEmpty()) return null;

        Set<String> athleteGroupIds = clubGroupRepository.findByClubIdAndMemberIdsContaining(
                session.getClubId(), athleteId).stream()
                .map(g -> g.getId())
                .collect(Collectors.toCollection(HashSet::new));

        for (GroupLinkedTraining glt : effective) {
            if (glt.getClubGroupId() != null && athleteGroupIds.contains(glt.getClubGroupId())) {
                return glt.getTrainingId();
            }
        }
        for (GroupLinkedTraining glt : effective) {
            if (glt.getClubGroupId() == null) return glt.getTrainingId();
        }
        return effective.getFirst().getTrainingId();
    }

    private static WorkoutSyncPayload toPayload(String athleteId, WorkoutSyncSourceType type, String sourceId,
                                                Training training, LocalDate date, String title, String notes) {
        return new WorkoutSyncPayload(athleteId, type, sourceId, training, date, title, notes);
    }
}
