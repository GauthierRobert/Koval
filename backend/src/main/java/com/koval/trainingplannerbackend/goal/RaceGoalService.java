package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class RaceGoalService {

    private final RaceGoalRepository repository;
    private final RaceService raceService;
    private final CompletedSessionRepository sessionRepository;

    public RaceGoalService(RaceGoalRepository repository, RaceService raceService,
                           CompletedSessionRepository sessionRepository) {
        this.repository = repository;
        this.raceService = raceService;
        this.sessionRepository = sessionRepository;
    }

    public List<RaceGoalResponse> getGoalsForAthlete(String athleteId) {
        Map<String, String> sessionByRace = raceEffortSessionsByRaceId(athleteId);
        return repository.findByAthleteId(athleteId).stream()
                .map(goal -> toResponse(goal, sessionByRace))
                .sorted(Comparator.comparing(
                        (RaceGoalResponse r) -> r.raceDate() == null ? "9999-99-99" : r.raceDate()))
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
        return toResponse(goal, raceEffortSessionsByRaceId(athleteId));
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

    /**
     * Races the athlete has set as goals whose scheduledDate matches {@code date}.
     * Used by session classification to surface a "Mark as race?" prompt on race day.
     */
    public List<Race> findRacesForAthleteOnDate(String athleteId, LocalDate date) {
        String iso = date.toString();
        return repository.findByAthleteId(athleteId).stream()
                .map(RaceGoal::getRaceId)
                .filter(Objects::nonNull)
                .map(this::loadRaceQuietly)
                .filter(Objects::nonNull)
                .filter(r -> iso.equals(r.getScheduledDate()))
                .toList();
    }

    /** True iff the athlete has set this race as a goal (used to authorize classification). */
    public boolean isRaceInGoals(String athleteId, String raceId) {
        return repository.existsByAthleteIdAndRaceId(athleteId, raceId);
    }

    /**
     * The race the athlete has set as a goal, or {@code null} if absent / not one of their goals.
     * Used by session classification to derive a session title from the event on RACE classification.
     */
    public Race findGoalRace(String athleteId, String raceId) {
        if (raceId == null || raceId.isBlank() || !isRaceInGoals(athleteId, raceId)) return null;
        return loadRaceQuietly(raceId);
    }

    private Race loadRaceQuietly(String raceId) {
        try {
            return raceService.getRaceById(raceId);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private RaceGoalResponse toResponse(RaceGoal goal, Map<String, String> sessionByRace) {
        Race race = null;
        String linkedSessionId = null;
        if (goal.getRaceId() != null) {
            try {
                race = raceService.getRaceById(goal.getRaceId());
            } catch (NoSuchElementException ignored) {}
            linkedSessionId = sessionByRace.get(goal.getRaceId());
        }
        return RaceGoalResponse.from(goal, race, linkedSessionId);
    }

    /**
     * Maps each race the athlete classified a session against (raceRole=RACE) to that session's id.
     * When a race day spans multiple race-effort sessions (e.g. a triathlon chain), the earliest wins.
     */
    private Map<String, String> raceEffortSessionsByRaceId(String athleteId) {
        Map<String, String> byRace = new HashMap<>();
        sessionRepository.findRaceEffortsByUserId(athleteId).stream()
                .filter(s -> s.getRaceId() != null)
                .sorted(Comparator.comparing(CompletedSession::getCompletedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(s -> byRace.putIfAbsent(s.getRaceId(), s.getId()));
        return byRace;
    }
}
