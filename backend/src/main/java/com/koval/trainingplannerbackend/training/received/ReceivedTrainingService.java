package com.koval.trainingplannerbackend.training.received;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of received trainings for athletes.
 * Handles both explicit coach-assigned trainings persisted as {@link ReceivedTraining}
 * documents and virtual entries derived from {@link ClubTrainingSession} participation.
 */
@Service
public class ReceivedTrainingService {

    private final ReceivedTrainingRepository receivedTrainingRepository;
    private final UserRepository userRepository;
    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubRepository clubRepository;

    public ReceivedTrainingService(ReceivedTrainingRepository receivedTrainingRepository,
                                   UserRepository userRepository,
                                   ClubTrainingSessionRepository sessionRepository,
                                   ClubRepository clubRepository) {
        this.receivedTrainingRepository = receivedTrainingRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.clubRepository = clubRepository;
    }

    /**
     * Creates {@link ReceivedTraining} records for the given athletes, skipping any
     * athlete who has already received the specified training. The assigner's display
     * name is resolved once and shared across all new records.
     *
     * @param trainingId  the training to assign
     * @param athleteIds  the athletes who should receive the training
     * @param assignedById the user ID of the person assigning the training
     * @param origin       the origin context (e.g. coach assignment, club session)
     * @param originId     identifier of the origin entity
     * @param originName   human-readable name of the origin entity
     */
    public void createReceivedTrainings(String trainingId,
                                        List<String> athleteIds,
                                        String assignedById,
                                        ReceivedTrainingOrigin origin,
                                        String originId,
                                        String originName) {
        String assignedByName = resolveAssignedByName(assignedById);
        Set<String> alreadyReceived = findAlreadyReceivedAthleteIds(trainingId, athleteIds);

        LocalDateTime now = LocalDateTime.now();
        List<ReceivedTraining> toSave = athleteIds.stream()
                .filter(id -> !alreadyReceived.contains(id))
                .map(id -> buildNewReceivedTraining(id, trainingId, assignedById, assignedByName, origin, originId, originName, now))
                .toList();
        if (!toSave.isEmpty()) {
            receivedTrainingRepository.saveAll(toSave);
        }
    }

    /**
     * Returns all trainings received by the given athlete, combining explicit
     * {@link ReceivedTraining} documents with virtual entries inferred from active
     * club session participation. Explicit entries take priority when a training
     * appears in both sources.
     *
     * @param athleteId the athlete whose received trainings are requested
     * @return a list of {@link ReceivedTrainingResponse} in insertion order
     */
    public List<ReceivedTrainingResponse> getReceivedTrainings(String athleteId) {
        Map<String, ReceivedTrainingResponse> byTrainingId = buildExplicitReceivedMap(athleteId);
        appendVirtualSessionEntries(byTrainingId, athleteId);
        return new ArrayList<>(byTrainingId.values());
    }

    /**
     * Checks whether the athlete has received the specified training, either via an
     * explicit {@link ReceivedTraining} record or through participation in an active
     * club session linked to that training.
     *
     * @param athleteId  the athlete to check
     * @param trainingId the training to check
     * @return {@code true} if the athlete has received or is participating in the training
     */
    public boolean hasReceived(String athleteId, String trainingId) {
        return receivedTrainingRepository.existsByAthleteIdAndTrainingId(athleteId, trainingId)
                || sessionRepository.existsByParticipantIdsContainingAndLinkedTrainingIdAndCancelledFalse(athleteId, trainingId);
    }

    // ── Private helpers for createReceivedTrainings ──────────────────────

    private String resolveAssignedByName(String assignedById) {
        return userRepository.findById(assignedById)
                .map(User::getDisplayName)
                .orElse("Unknown");
    }

    private Set<String> findAlreadyReceivedAthleteIds(String trainingId, List<String> athleteIds) {
        return receivedTrainingRepository
                .findByTrainingIdAndAthleteIdIn(trainingId, athleteIds)
                .stream()
                .map(ReceivedTraining::getAthleteId)
                .collect(Collectors.toSet());
    }

    private ReceivedTraining buildNewReceivedTraining(String athleteId,
                                                      String trainingId,
                                                      String assignedById,
                                                      String assignedByName,
                                                      ReceivedTrainingOrigin origin,
                                                      String originId,
                                                      String originName,
                                                      LocalDateTime now) {
        ReceivedTraining rt = new ReceivedTraining();
        rt.setAthleteId(athleteId);
        rt.setTrainingId(trainingId);
        rt.setAssignedBy(assignedById);
        rt.setAssignedByName(assignedByName);
        rt.setOrigin(origin);
        rt.setOriginId(originId);
        rt.setOriginName(originName);
        rt.setReceivedAt(now);
        return rt;
    }

    // ── Private helpers for getReceivedTrainings ────────────────────────

    private Map<String, ReceivedTrainingResponse> buildExplicitReceivedMap(String athleteId) {
        return receivedTrainingRepository.findByAthleteId(athleteId).stream()
                .collect(Collectors.toMap(
                        ReceivedTraining::getTrainingId,
                        this::toExplicitResponse,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private ReceivedTrainingResponse toExplicitResponse(ReceivedTraining rt) {
        return new ReceivedTrainingResponse(
                rt.getId(),
                rt.getTrainingId(),
                rt.getAssignedByName(),
                rt.getOrigin(),
                rt.getOriginName(),
                rt.getReceivedAt()
        );
    }

    private void appendVirtualSessionEntries(Map<String, ReceivedTrainingResponse> byTrainingId,
                                             String athleteId) {
        List<ClubTrainingSession> sessions =
                sessionRepository.findByParticipantIdsContainingAndLinkedTrainingIdIsNotNullAndCancelledFalse(athleteId);
        if (sessions.isEmpty()) return;

        Map<String, String> clubNames = batchLoadClubNames(sessions);
        Map<String, String> coachNames = batchLoadCoachNames(sessions);

        sessions.stream()
                .filter(s -> !byTrainingId.containsKey(s.getLinkedTrainingId()))
                .forEach(s -> byTrainingId.put(s.getLinkedTrainingId(), toVirtualResponse(s, clubNames, coachNames)));
    }

    private Map<String, String> batchLoadClubNames(List<ClubTrainingSession> sessions) {
        List<String> clubIds = sessions.stream()
                .map(ClubTrainingSession::getClubId)
                .distinct()
                .toList();
        return clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Club::getName));
    }

    private Map<String, String> batchLoadCoachNames(List<ClubTrainingSession> sessions) {
        List<String> coachIds = sessions.stream()
                .map(ClubTrainingSession::getResponsibleCoachId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return userRepository.findAllById(coachIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
    }

    private ReceivedTrainingResponse toVirtualResponse(ClubTrainingSession session,
                                                       Map<String, String> clubNames,
                                                       Map<String, String> coachNames) {
        String coachName = session.getResponsibleCoachId() != null
                ? coachNames.getOrDefault(session.getResponsibleCoachId(), "Unknown")
                : null;
        String clubName = clubNames.getOrDefault(session.getClubId(), "Unknown");

        return new ReceivedTrainingResponse(
                "session:" + session.getId(),
                session.getLinkedTrainingId(),
                coachName,
                ReceivedTrainingOrigin.CLUB_SESSION,
                clubName,
                session.getScheduledAt()
        );
    }
}
