package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @PostMapping
    public ResponseEntity<Training> createTraining(@RequestBody Training training) {
        String userId = SecurityUtils.getCurrentUserId();
        Training created = trainingService.createTraining(training, userId);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Training> getTraining(@PathVariable String id) {
        try {
            Training training = trainingService.getTrainingById(id);
            // Resolve zones for the current user (viewer)
            String userId = SecurityUtils.getCurrentUserId();
            trainingService.resolveTraining(training, userId);

            return ResponseEntity.ok(training);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTraining(@PathVariable String id) {
        try {
            trainingService.deleteTraining(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Training>> listTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.listTrainingsByUser(userId)
                .stream().map(t -> trainingService.resolveTraining(t, userId))
                .toList();
        return ResponseEntity.ok(trainings);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Training>> searchByTag(@RequestParam String tag) {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.searchByTag(tag).stream()
                .map(t -> trainingService.resolveTraining(t, userId))
                .toList();
        return ResponseEntity.ok(trainings);
    }

    @GetMapping("/search/type")
    public ResponseEntity<List<Training>> searchByType(@RequestParam TrainingType type) {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.searchByType(type)
                .stream().map(t -> trainingService.resolveTraining(t, userId))
                .toList();
        return ResponseEntity.ok(trainings);
    }

    @GetMapping("/discover")
    public ResponseEntity<List<Training>> discoverTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Training> trainings = trainingService.discoverTrainingsByUserTags(userId)
                .stream().map(t -> trainingService.resolveTraining(t, userId))
                .toList();
        return ResponseEntity.ok(trainings);
    }

    @GetMapping("/folders")
    public ResponseEntity<Map<String, List<Training>>> getTrainingFolders() {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            Map<String, List<Training>> folders = trainingService.getTrainingFolders(userId);
            folders.values().forEach(list -> list.forEach(t -> trainingService.resolveTraining(t, userId)));
            return ResponseEntity.ok(folders);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of());
        }
    }
}
