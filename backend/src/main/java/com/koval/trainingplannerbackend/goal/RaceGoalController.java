package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<List<RaceGoalResponse>> getGoals() {
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
        return ResponseEntity.ok(service.updateGoal(id, userId, goal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        service.deleteGoal(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/link-race/{raceId}")
    public ResponseEntity<RaceGoal> linkToRace(@PathVariable String id, @PathVariable String raceId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(service.linkToRace(id, userId, raceId));
    }
}
