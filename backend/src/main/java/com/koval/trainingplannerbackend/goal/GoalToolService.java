package com.koval.trainingplannerbackend.goal;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * AI-facing tool service for Race Goal operations.
 * Allows the AI to create, list, update, and delete race goals on behalf of the athlete.
 */
@Service
public class GoalToolService {

    private final RaceGoalRepository repository;

    public GoalToolService(RaceGoalRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "List all race goals for a user, ordered by race date ascending.")
    public List<GoalSummary> listGoals(
            @ToolParam(description = "Athlete user ID") String userId) {
        return repository.findByAthleteIdOrderByRaceDateAsc(userId)
                .stream()
                .map(GoalSummary::from)
                .toList();
    }

    @Tool(description = "Create a new race goal for a user. Priority: A=goal race, B=target race, C=training race. Sport: CYCLING, RUNNING, SWIMMING, TRIATHLON, OTHER.")
    public GoalSummary createGoal(
            @ToolParam(description = "Athlete user ID") String userId,
            @ToolParam(description = "Race title (e.g. 'Paris-Roubaix 2026')") String title,
            @ToolParam(description = "Sport: CYCLING | RUNNING | SWIMMING | TRIATHLON | OTHER") String sport,
            @ToolParam(description = "Race date (YYYY-MM-DD)") LocalDate raceDate,
            @ToolParam(description = "Priority: A | B | C") String priority,
            @ToolParam(description = "Distance (optional, e.g. '100km')") String distance,
            @ToolParam(description = "Location (optional, e.g. 'Paris, France')") String location,
            @ToolParam(description = "Target finish time (optional, e.g. '3:30:00')") String targetTime,
            @ToolParam(description = "Notes or strategy (optional)") String notes) {

        RaceGoal goal = new RaceGoal();
        goal.setAthleteId(userId);
        goal.setTitle(title);
        goal.setSport(sport);
        goal.setRaceDate(raceDate);
        goal.setPriority(priority);
        goal.setDistance(distance);
        goal.setLocation(location);
        goal.setTargetTime(targetTime);
        goal.setNotes(notes);
        goal.setCreatedAt(java.time.LocalDateTime.now());

        return GoalSummary.from(repository.save(goal));
    }

    @Tool(description = "Update an existing race goal. Pass only the fields to change; omit unchanged fields.")
    public GoalSummary updateGoal(
            @ToolParam(description = "Goal ID") String goalId,
            @ToolParam(description = "Athlete user ID (ownership check)") String userId,
            @ToolParam(description = "New title (optional)") String title,
            @ToolParam(description = "New sport (optional)") String sport,
            @ToolParam(description = "New race date YYYY-MM-DD (optional)") LocalDate raceDate,
            @ToolParam(description = "New priority A|B|C (optional)") String priority,
            @ToolParam(description = "New distance (optional)") String distance,
            @ToolParam(description = "New location (optional)") String location,
            @ToolParam(description = "New target time (optional)") String targetTime,
            @ToolParam(description = "New notes (optional)") String notes) {

        RaceGoal goal = repository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        if (!goal.getAthleteId().equals(userId)) {
            throw new IllegalStateException("Goal does not belong to user " + userId);
        }

        if (title != null)      goal.setTitle(title);
        if (sport != null)      goal.setSport(sport);
        if (raceDate != null)   goal.setRaceDate(raceDate);
        if (priority != null)   goal.setPriority(priority);
        if (distance != null)   goal.setDistance(distance);
        if (location != null)   goal.setLocation(location);
        if (targetTime != null) goal.setTargetTime(targetTime);
        if (notes != null)      goal.setNotes(notes);

        return GoalSummary.from(repository.save(goal));
    }

    @Tool(description = "Delete a race goal by ID.")
    public String deleteGoal(
            @ToolParam(description = "Goal ID") String goalId,
            @ToolParam(description = "Athlete user ID (ownership check)") String userId) {

        RaceGoal goal = repository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        if (!goal.getAthleteId().equals(userId)) {
            throw new IllegalStateException("Goal does not belong to user " + userId);
        }
        repository.deleteById(goalId);
        return "Deleted goal: " + goal.getTitle();
    }

    /** Lean summary to minimize token usage. */
    public record GoalSummary(
            String id,
            String title,
            String sport,
            String raceDate,
            String priority,
            String distance,
            String location,
            String targetTime,
            String notes,
            long daysUntil
    ) {
        static GoalSummary from(RaceGoal g) {
            long days = LocalDate.now().until(g.getRaceDate()).getDays();
            return new GoalSummary(
                    g.getId(), g.getTitle(), g.getSport(),
                    g.getRaceDate().toString(), g.getPriority(),
                    g.getDistance(), g.getLocation(), g.getTargetTime(), g.getNotes(),
                    days);
        }
    }
}
