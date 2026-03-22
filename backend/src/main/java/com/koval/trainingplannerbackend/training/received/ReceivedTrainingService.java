package com.koval.trainingplannerbackend.training.received;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.ClubTrainingSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    public void createReceivedTrainings(String trainingId,
                                        List<String> athleteIds,
                                        String assignedById,
                                        ReceivedTrainingOrigin origin,
                                        String originId,
                                        String originName) {
        String assignedByName = userRepository.findById(assignedById)
                .map(User::getDisplayName)
                .orElse("Unknown");

        Set<String> alreadyReceived = receivedTrainingRepository
                .findByTrainingIdAndAthleteIdIn(trainingId, athleteIds)
                .stream()
                .map(ReceivedTraining::getAthleteId)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        List<ReceivedTraining> toSave = new ArrayList<>();
        for (String athleteId : athleteIds) {
            if (alreadyReceived.contains(athleteId)) continue;

            ReceivedTraining rt = new ReceivedTraining();
            rt.setAthleteId(athleteId);
            rt.setTrainingId(trainingId);
            rt.setAssignedBy(assignedById);
            rt.setAssignedByName(assignedByName);
            rt.setOrigin(origin);
            rt.setOriginId(originId);
            rt.setOriginName(originName);
            rt.setReceivedAt(now);
            toSave.add(rt);
        }
        if (!toSave.isEmpty()) {
            receivedTrainingRepository.saveAll(toSave);
        }
    }

    public List<ReceivedTrainingResponse> getReceivedTrainings(String athleteId) {
        // Real ReceivedTraining docs — keyed by trainingId so they take priority
        Map<String, ReceivedTrainingResponse> byTrainingId = new LinkedHashMap<>();

        List<ReceivedTraining> received = receivedTrainingRepository.findByAthleteId(athleteId);
        for (ReceivedTraining rt : received) {
            byTrainingId.put(rt.getTrainingId(), new ReceivedTrainingResponse(
                    rt.getId(),
                    rt.getTrainingId(),
                    rt.getAssignedByName(),
                    rt.getOrigin(),
                    rt.getOriginName(),
                    rt.getReceivedAt()
            ));
        }

        // Virtual received trainings from club sessions
        List<ClubTrainingSession> sessions =
                sessionRepository.findByParticipantIdsContainingAndLinkedTrainingIdIsNotNullAndCancelledFalse(athleteId);

        if (!sessions.isEmpty()) {
            // Batch-load club names
            List<String> clubIds = sessions.stream()
                    .map(ClubTrainingSession::getClubId)
                    .distinct()
                    .toList();
            Map<String, String> clubNames = clubRepository.findAllById(clubIds).stream()
                    .collect(Collectors.toMap(Club::getId, Club::getName));

            // Batch-load coach names
            List<String> coachIds = sessions.stream()
                    .map(ClubTrainingSession::getResponsibleCoachId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<String, String> coachNames = userRepository.findAllById(coachIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getDisplayName));

            for (ClubTrainingSession session : sessions) {
                String trainingId = session.getLinkedTrainingId();
                // Real entries take priority — skip if already present
                if (byTrainingId.containsKey(trainingId)) continue;

                String coachName = session.getResponsibleCoachId() != null
                        ? coachNames.getOrDefault(session.getResponsibleCoachId(), "Unknown")
                        : null;
                String clubName = clubNames.getOrDefault(session.getClubId(), "Unknown");

                byTrainingId.put(trainingId, new ReceivedTrainingResponse(
                        "session:" + session.getId(),
                        trainingId,
                        coachName,
                        ReceivedTrainingOrigin.CLUB_SESSION,
                        clubName,
                        session.getScheduledAt()
                ));
            }
        }

        return new ArrayList<>(byTrainingId.values());
    }

    public boolean hasReceived(String athleteId, String trainingId) {
        return receivedTrainingRepository.existsByAthleteIdAndTrainingId(athleteId, trainingId)
                || sessionRepository.existsByParticipantIdsContainingAndLinkedTrainingIdAndCancelledFalse(athleteId, trainingId);
    }
}
