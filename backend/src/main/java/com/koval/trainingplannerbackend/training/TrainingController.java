package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
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

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trainings")
@CrossOrigin(origins = "*")
public class TrainingController {

    private final TrainingService trainingService;
    private final UserService userService;
    private final CoachService coachService;

    public TrainingController(TrainingService trainingService, UserService userService, CoachService coachService) {
        this.trainingService = trainingService;
        this.userService = userService;
        this.coachService = coachService;
    }

    @PostMapping
    public ResponseEntity<Training> createTraining(@Valid @RequestBody Training training) {
        String userId = SecurityUtils.getCurrentUserId();
        Training created = trainingService.createTraining(training, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Training> getTraining(@PathVariable String id) {
        try {
            Training training = trainingService.getTrainingById(id);
            String userId = SecurityUtils.getCurrentUserId();
            verifyAccessToTraining(userId, training);
            trainingService.enrichTrainingForUser(training, userId);
            return ResponseEntity.ok(training);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Training> updateTraining(@PathVariable String id,
                                                   @Valid @RequestBody Training updates) {
        try {
            Training existing = trainingService.getTrainingById(id);
            String userId = SecurityUtils.getCurrentUserId();
            verifyAccessToTraining(userId, existing);
            Training updated = trainingService.updateTraining(id, updates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTraining(@PathVariable String id) {
        try {
            Training existing = trainingService.getTrainingById(id);
            String userId = SecurityUtils.getCurrentUserId();
            verifyAccessToTraining(userId, existing);
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

    @GetMapping(params = "page")
    public ResponseEntity<Page<Training>> listTrainings(Pageable pageable) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(trainingService.listTrainingsByUser(userId, pageable));
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

    private void verifyAccessToTraining(String currentUserId, Training training) {
        if (currentUserId.equals(training.getCreatedBy())) {
            return;
        }
        if (coachService.isCoachOfAthlete(currentUserId, training.getCreatedBy())) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this training");
    }
}
