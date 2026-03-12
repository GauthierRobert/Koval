package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/goals")
@CrossOrigin(origins = "*")
public class RaceGoalController {

    private final RaceGoalService service;
    private final RaceService raceService;

    public RaceGoalController(RaceGoalService service, RaceService raceService) {
        this.service = service;
        this.raceService = raceService;
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

    @PostMapping("/{id}/link-race/{raceId}")
    public ResponseEntity<RaceGoal> linkToRace(@PathVariable String id, @PathVariable String raceId) {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            Race race = raceService.getRaceById(raceId);
            RaceGoal goal = service.getGoalsForAthlete(userId).stream()
                    .filter(g -> g.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

            goal.setRaceId(raceId);
            // Copy race metadata into goal if not already set
            if (goal.getSport() == null && race.getSport() != null) goal.setSport(race.getSport());
            if (goal.getLocation() == null && race.getLocation() != null) goal.setLocation(race.getLocation());
            if (goal.getDistance() == null && race.getDistance() != null) goal.setDistance(race.getDistance());

            return ResponseEntity.ok(service.updateGoal(id, userId, goal));
        } catch (NoSuchElementException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
