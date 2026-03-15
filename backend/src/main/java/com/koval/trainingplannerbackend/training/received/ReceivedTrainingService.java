package com.koval.trainingplannerbackend.training.received;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReceivedTrainingService {

    private final ReceivedTrainingRepository receivedTrainingRepository;
    private final TrainingRepository trainingRepository;
    private final UserRepository userRepository;

    public ReceivedTrainingService(ReceivedTrainingRepository receivedTrainingRepository,
                                   TrainingRepository trainingRepository,
                                   UserRepository userRepository) {
        this.receivedTrainingRepository = receivedTrainingRepository;
        this.trainingRepository = trainingRepository;
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

        List<String> trainingIds = received.stream().map(ReceivedTraining::getTrainingId).distinct().toList();
        Map<String, Training> trainingMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Function.identity()));

        return received.stream()
                .map(rt -> {
                    Training t = trainingMap.get(rt.getTrainingId());
                    return new ReceivedTrainingResponse(
                            rt.getId(),
                            rt.getTrainingId(),
                            t != null ? t.getTitle() : "Deleted Training",
                            t != null ? t.getDescription() : null,
                            t != null ? t.getSportType() : null,
                            t != null ? t.getTrainingType() : null,
                            t != null ? t.getEstimatedTss() : null,
                            t != null ? t.getEstimatedIf() : null,
                            t != null ? t.getEstimatedDurationSeconds() : null,
                            rt.getAssignedByName(),
                            rt.getOrigin(),
                            rt.getOriginName(),
                            rt.getReceivedAt()
                    );
                })
                .toList();
    }

    public boolean hasReceived(String athleteId, String trainingId) {
        return receivedTrainingRepository.existsByAthleteIdAndTrainingId(athleteId, trainingId);
    }
}
