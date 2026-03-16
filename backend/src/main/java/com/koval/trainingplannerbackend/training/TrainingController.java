package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.metrics.TrainingMetricsService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingResponse;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trainings")
@CrossOrigin(origins = "*")
public class TrainingController {

    private final TrainingService trainingService;
    private final TrainingMetricsService metricsService;
    private final CoachService coachService;
    private final ReceivedTrainingService receivedTrainingService;

    public TrainingController(TrainingService trainingService,
                              TrainingMetricsService metricsService,
                              CoachService coachService,
                              ReceivedTrainingService receivedTrainingService) {
        this.trainingService = trainingService;
        this.metricsService = metricsService;
        this.coachService = coachService;
        this.receivedTrainingService = receivedTrainingService;
    }

    @PostMapping
    public ResponseEntity<Training> createTraining(@Valid @RequestBody Training training) {
        String userId = SecurityUtils.getCurrentUserId();
        Training created = trainingService.createTraining(training, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Training> getTraining(@PathVariable String id) {
        Training training = trainingService.getTrainingById(id);
        String userId = SecurityUtils.getCurrentUserId();
        verifyAccessToTraining(userId, training);
        metricsService.enrichTrainingForUser(training, userId);
        return ResponseEntity.ok(training);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Training> updateTraining(@PathVariable String id,
                                                   @RequestBody Training updates) {
        Training existing = trainingService.getTrainingById(id);
        String userId = SecurityUtils.getCurrentUserId();
        verifyAccessToTraining(userId, existing);
        Training updated = trainingService.updateTraining(id, updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTraining(@PathVariable String id) {
        Training existing = trainingService.getTrainingById(id);
        String userId = SecurityUtils.getCurrentUserId();
        verifyAccessToTraining(userId, existing);
        trainingService.deleteTraining(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Training>> listTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.listTrainingsByUser(userId);
        trainings.forEach(t -> metricsService.enrichTrainingForUser(t, userId));
        return ResponseEntity.ok(trainings);
    }

    @GetMapping("/received")
    public ResponseEntity<List<ReceivedTrainingResponse>> getReceivedTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(receivedTrainingService.getReceivedTrainings(userId));
    }

    @GetMapping("/club-trainings")
    public ResponseEntity<List<Training>> listClubTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.discoverClubTrainings(userId);
        trainings.forEach(t -> metricsService.enrichTrainingForUser(t, userId));
        return ResponseEntity.ok(trainings);
    }

    @GetMapping(params = "page")
    public ResponseEntity<Page<Training>> listTrainings(Pageable pageable) {
        String userId = SecurityUtils.getCurrentUserId();
        Page<Training> page = trainingService.listTrainingsByUser(userId, pageable);
        page.getContent().forEach(t -> metricsService.enrichTrainingForUser(t, userId));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Training>> searchByGroup(@RequestParam String group) {
        return ResponseEntity.ok(trainingService.searchByGroup(group));
    }

    @GetMapping("/search/type")
    public ResponseEntity<List<Training>> searchByType(@RequestParam TrainingType type) {
        return ResponseEntity.ok(trainingService.searchByType(type));
    }

    @GetMapping("/discover")
    public ResponseEntity<List<Training>> discoverTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(trainingService.discoverTrainingsByUserGroups(userId));
    }

    private void verifyAccessToTraining(String currentUserId, Training training) {
        if (currentUserId.equals(training.getCreatedBy())) {
            return;
        }
        if (coachService.isCoachOfAthlete(currentUserId, training.getCreatedBy())) {
            return;
        }
        if (training.getClubIds() != null) {
            for (String cid : training.getClubIds()) {
                if (trainingService.isUserActiveClubMember(currentUserId, cid)) return;
            }
        }
        if (receivedTrainingService.hasReceived(currentUserId, training.getId())) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this training");
    }
}
