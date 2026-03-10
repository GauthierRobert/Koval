package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@CrossOrigin(origins = "*")
public class RaceGoalController {

    private final RaceGoalService service;

    public RaceGoalController(RaceGoalService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RaceGoal>> getGoals() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(service.getGoalsForAthlete(userId));
    }

    @PostMapping
    public ResponseEntity<RaceGoal> createGoal(@Valid @RequestBody RaceGoal goal) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGoal(userId, goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RaceGoal> updateGoal(@PathVariable String id, @Valid @RequestBody RaceGoal goal) {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            return ResponseEntity.ok(service.updateGoal(id, userId, goal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            service.deleteGoal(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
