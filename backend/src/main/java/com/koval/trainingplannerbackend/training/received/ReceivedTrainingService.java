package com.koval.trainingplannerbackend.training.received;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReceivedTrainingService {

    private final ReceivedTrainingRepository receivedTrainingRepository;
    private final UserRepository userRepository;

    public ReceivedTrainingService(ReceivedTrainingRepository receivedTrainingRepository,
                                   UserRepository userRepository) {
        this.receivedTrainingRepository = receivedTrainingRepository;
        this.userRepository = userRepository;
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

        for (String athleteId : athleteIds) {
            if (receivedTrainingRepository.existsByAthleteIdAndTrainingId(athleteId, trainingId)) {
                continue;
            }

            ReceivedTraining rt = new ReceivedTraining();
            rt.setAthleteId(athleteId);
            rt.setTrainingId(trainingId);
            rt.setAssignedBy(assignedById);
            rt.setAssignedByName(assignedByName);
            rt.setOrigin(origin);
            rt.setOriginId(originId);
            rt.setOriginName(originName);
            rt.setReceivedAt(LocalDateTime.now());
            receivedTrainingRepository.save(rt);
        }
    }

    public List<ReceivedTrainingResponse> getReceivedTrainings(String athleteId) {
        List<ReceivedTraining> received = receivedTrainingRepository.findByAthleteId(athleteId);
        if (received.isEmpty()) return List.of();

        return received.stream()
                .map(rt -> new ReceivedTrainingResponse(
                        rt.getId(),
                        rt.getTrainingId(),
                        rt.getAssignedByName(),
                        rt.getOrigin(),
                        rt.getOriginName(),
                        rt.getReceivedAt()
                ))
                .toList();
    }

    public boolean hasReceived(String athleteId, String trainingId) {
        return receivedTrainingRepository.existsByAthleteIdAndTrainingId(athleteId, trainingId);
    }
}
