package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Training CRUD operations.
 */
@RestController
@RequestMapping("/api/trainings")
@CrossOrigin(origins = "*")
public class TrainingController {

    private final TrainingManagementService trainingService;
    private final UserService userService;

    public TrainingController(TrainingManagementService trainingService, UserService userService) {
        this.trainingService = trainingService;
        this.userService = userService;
    }

    /**
     * Create a new training.
     * TODO: Get userId from JWT token in real implementation
     */
    @PostMapping
    public ResponseEntity<Training> createTraining(@RequestBody Training training,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String ownerId = userId != null ? userId : "anonymous";
        Training created = trainingService.createTraining(training, ownerId);
        return ResponseEntity.ok(created);
    }

    /**
     * Get a training by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Training> getTraining(@PathVariable String id) {
        try {
            Training training = trainingService.getTrainingById(id);
            return ResponseEntity.ok(training);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update a training.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Training> updateTraining(@PathVariable String id,
            @RequestBody Training updates) {
        try {
            Training updated = trainingService.updateTraining(id, updates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a training.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTraining(@PathVariable String id) {
        try {
            trainingService.deleteTraining(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List trainings for a user.
     * TODO: Get userId from JWT token in real implementation
     */
    @GetMapping
    public ResponseEntity<List<Training>> listTrainings(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null || userId.isEmpty()) {
            // Return public trainings if no user specified
            return ResponseEntity.ok(trainingService.listPublicTrainings());
        }
        return ResponseEntity.ok(trainingService.listTrainingsByUser(userId));
    }

    /**
     * Search trainings by tag.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Training>> searchByTag(@RequestParam String tag) {
        return ResponseEntity.ok(trainingService.searchByTag(tag));
    }

    /**
     * Search trainings by type.
     */
    @GetMapping("/search/type")
    public ResponseEntity<List<Training>> searchByType(@RequestParam TrainingType type) {
        return ResponseEntity.ok(trainingService.searchByType(type));
    }

    /**
     * Discover trainings based on user's tags.
     */
    @GetMapping("/discover")
    public ResponseEntity<List<Training>> discoverTrainings(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }
        List<Training> discovered = trainingService.discoverTrainingsByUserTags(
                user.getTags(), userId, user.getCoachId());
        return ResponseEntity.ok(discovered);
    }
}
