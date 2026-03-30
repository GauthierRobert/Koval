package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RaceGoalService {

    private final RaceGoalRepository repository;

    public RaceGoalService(RaceGoalRepository repository) {
        this.repository = repository;
    }

    public List<RaceGoal> getGoalsForAthlete(String athleteId) {
        return repository.findByAthleteIdOrderByRaceDateAsc(athleteId);
    }

    public RaceGoal createGoal(String athleteId, RaceGoal goal) {
        if (goal.getRaceId() != null && repository.existsByAthleteIdAndRaceId(athleteId, goal.getRaceId())) {
            throw new ValidationException("This race is already in your goals");
        }
        goal.setAthleteId(athleteId);
        goal.setCreatedAt(LocalDateTime.now());
        return repository.save(goal);
    }

    public RaceGoal updateGoal(String id, String athleteId, RaceGoal update) {
        RaceGoal existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        if (!existing.getAthleteId().equals(athleteId)) {
            throw new ForbiddenOperationException("Not authorized");
        }
        update.setId(id);
        update.setAthleteId(athleteId);
        update.setCreatedAt(existing.getCreatedAt());
        return repository.save(update);
    }

    public void deleteGoal(String id, String athleteId) {
        RaceGoal existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        if (!existing.getAthleteId().equals(athleteId)) {
            throw new ForbiddenOperationException("Not authorized");
        }
        repository.deleteById(id);
    }
}
