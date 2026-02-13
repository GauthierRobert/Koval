package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        return ResponseEntity.ok(trainingService.listTrainingsByUser(userId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Training>> searchByTag(@RequestParam String tag) {
        return ResponseEntity.ok(trainingService.searchByTag(tag));
    }

    @GetMapping("/search/type")
    public ResponseEntity<List<Training>> searchByType(@RequestParam TrainingType type) {
        return ResponseEntity.ok(trainingService.searchByType(type));
    }

    @GetMapping("/discover")
    public ResponseEntity<List<Training>> discoverTrainings() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(trainingService.discoverTrainingsByUserTags(userId));
    }

    @GetMapping("/folders")
    public ResponseEntity<Map<String, List<Training>>> getTrainingFolders() {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            return ResponseEntity.ok(trainingService.getTrainingFolders(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of());
        }
    }
}
