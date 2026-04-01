package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RaceGoalService {

    private final RaceGoalRepository repository;
    private final RaceService raceService;

    public RaceGoalService(RaceGoalRepository repository, RaceService raceService) {
        this.repository = repository;
        this.raceService = raceService;
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

    public RaceGoal linkToRace(String goalId, String athleteId, String raceId) {
        RaceGoal goal = repository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        if (!goal.getAthleteId().equals(athleteId)) {
            throw new ForbiddenOperationException("Not authorized");
        }

        Race race = raceService.getRaceById(raceId);
        goal.setRaceId(raceId);
        if (goal.getSport() == null && race.getSport() != null) goal.setSport(race.getSport());
        if (goal.getLocation() == null && race.getLocation() != null) goal.setLocation(race.getLocation());
        if (goal.getDistance() == null && race.getDistance() != null) goal.setDistance(race.getDistance());

        return repository.save(goal);
    }
}
