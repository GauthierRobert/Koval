package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class RaceGoalService {

    private final RaceGoalRepository repository;
    private final RaceService raceService;

    public RaceGoalService(RaceGoalRepository repository, RaceService raceService) {
        this.repository = repository;
        this.raceService = raceService;
    }

    public List<RaceGoalResponse> getGoalsForAthlete(String athleteId) {
        return repository.findByAthleteIdOrderByRaceDateAsc(athleteId).stream()
                .map(goal -> {
                    Race race = null;
                    if (goal.getRaceId() != null) {
                        try {
                            race = raceService.getRaceById(goal.getRaceId());
                        } catch (NoSuchElementException ignored) {}
                    }
                    return RaceGoalResponse.from(goal, race);
                })
                .toList();
    }

    @CacheEvict(value = "athleteGoals", key = "#athleteId")
    public RaceGoal createGoal(String athleteId, RaceGoal goal) {
        if (goal.getRaceId() != null && repository.existsByAthleteIdAndRaceId(athleteId, goal.getRaceId())) {
            throw new ValidationException("This race is already in your goals");
        }
        goal.setAthleteId(athleteId);
        goal.setCreatedAt(LocalDateTime.now());
        return repository.save(goal);
    }

    @CacheEvict(value = "athleteGoals", key = "#athleteId")
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

    @CacheEvict(value = "athleteGoals", key = "#athleteId")
    public void deleteGoal(String id, String athleteId) {
        RaceGoal existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        if (!existing.getAthleteId().equals(athleteId)) {
            throw new ForbiddenOperationException("Not authorized");
        }
        repository.deleteById(id);
    }

    /** Get a single race goal by id, scoped to the owning athlete. */
    public RaceGoalResponse getGoal(String id, String athleteId) {
        RaceGoal goal = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        if (!goal.getAthleteId().equals(athleteId)) {
            throw new ForbiddenOperationException("Not authorized");
        }
        Race race = null;
        if (goal.getRaceId() != null) {
            try {
                race = raceService.getRaceById(goal.getRaceId());
            } catch (NoSuchElementException ignored) {}
        }
        return RaceGoalResponse.from(goal, race);
    }

    @CacheEvict(value = "athleteGoals", key = "#athleteId")
    public RaceGoal linkToRace(String goalId, String athleteId, String raceId) {
        RaceGoal goal = repository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        if (!goal.getAthleteId().equals(athleteId)) {
            throw new ForbiddenOperationException("Not authorized");
        }
        goal.setRaceId(raceId);
        return repository.save(goal);
    }
}
