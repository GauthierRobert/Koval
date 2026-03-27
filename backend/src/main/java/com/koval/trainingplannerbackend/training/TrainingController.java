package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.metrics.TrainingMetricsService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingResponse;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST API for training plan CRUD operations, discovery, and received training queries. */
@RestController
@RequestMapping("/api/trainings")
@CrossOrigin(origins = "*")
public class TrainingController {

    private final TrainingService trainingService;
    private final TrainingMetricsService metricsService;
    private final TrainingAccessService trainingAccessService;
    private final ReceivedTrainingService receivedTrainingService;

    public TrainingController(TrainingService trainingService,
                              TrainingMetricsService metricsService,
                              TrainingAccessService trainingAccessService,
                              ReceivedTrainingService receivedTrainingService) {
        this.trainingService = trainingService;
        this.metricsService = metricsService;
        this.trainingAccessService = trainingAccessService;
        this.receivedTrainingService = receivedTrainingService;
    }

    /** Creates a new training plan for the authenticated user. */
    @PostMapping
    public ResponseEntity<Training> createTraining(@Valid @RequestBody Training training) {
        String userId = SecurityUtils.getCurrentUserId();
        Training created = trainingService.createTraining(training, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Retrieves a training by ID, enriched with the requesting user's zone-based metrics. */
    @GetMapping("/{id}")
    public ResponseEntity<Training> getTraining(@PathVariable String id) {
        Training training = trainingService.getTrainingById(id);
        String userId = SecurityUtils.getCurrentUserId();
        trainingAccessService.verifyAccess(userId,training);
        metricsService.enrichTrainingForUser(training, userId);
        return ResponseEntity.ok(training);
    }

    /** Updates an existing training (partial update: only non-null fields are applied). */
    @PutMapping("/{id}")
    public ResponseEntity<Training> updateTraining(@PathVariable String id,
                                                   @RequestBody Training updates) {
        Training existing = trainingService.getTrainingById(id);
        String userId = SecurityUtils.getCurrentUserId();
        trainingAccessService.verifyAccess(userId,existing);
        Training updated = trainingService.updateTraining(id, updates);
        return ResponseEntity.ok(updated);
    }

    /** Deletes a training by ID after verifying access. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTraining(@PathVariable String id) {
        Training existing = trainingService.getTrainingById(id);
        String userId = SecurityUtils.getCurrentUserId();
        trainingAccessService.verifyAccess(userId,existing);
        trainingService.deleteTraining(id);
        return ResponseEntity.noContent().build();
    }

    /** Lists all trainings created by the authenticated user, enriched with user-specific metrics. */
    @GetMapping
    public ResponseEntity<List<Training>> listTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.listTrainingsByUser(userId);
        trainings.forEach(t -> metricsService.enrichTrainingForUser(t, userId));
        return ResponseEntity.ok(trainings);
    }

    /** Lists trainings received by the authenticated athlete (from coach assignments and club sessions). */
    @GetMapping("/received")
    public ResponseEntity<List<ReceivedTrainingResponse>> getReceivedTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(receivedTrainingService.getReceivedTrainings(userId));
    }

    /** Discovers trainings from clubs the user is an active member of. */
    @GetMapping("/club-trainings")
    public ResponseEntity<List<Training>> listClubTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.discoverClubTrainings(userId);
        trainings.forEach(t -> metricsService.enrichTrainingForUser(t, userId));
        return ResponseEntity.ok(trainings);
    }

    /** Paginated variant of {@link #listTrainings()}. */
    @GetMapping(params = "page")
    public ResponseEntity<Page<Training>> listTrainings(Pageable pageable) {
        String userId = SecurityUtils.getCurrentUserId();
        Page<Training> page = trainingService.listTrainingsByUser(userId, pageable);
        page.getContent().forEach(t -> metricsService.enrichTrainingForUser(t, userId));
        return ResponseEntity.ok(page);
    }

}
