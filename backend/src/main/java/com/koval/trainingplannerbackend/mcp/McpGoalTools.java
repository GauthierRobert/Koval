package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalResponse;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * MCP tool adapter for race goal operations.
 */
@Service
public class McpGoalTools {

    private final RaceGoalService raceGoalService;

    public McpGoalTools(RaceGoalService raceGoalService) {
        this.raceGoalService = raceGoalService;
    }

    @Tool(description = "List the user's race goals sorted by date. Goals have priority A (main target), B (important), or C (training race). Shows days until each race.")
    public List<GoalSummary> listGoals() {
        String userId = SecurityUtils.getCurrentUserId();
        return raceGoalService.getGoalsForAthlete(userId).stream()
                .map(GoalSummary::from)
                .toList();
    }

    @Tool(description = "Get a single race goal by id, including the linked race details if any.")
    public GoalSummary getGoal(
            @ToolParam(description = "Goal ID") String goalId) {
        String userId = SecurityUtils.getCurrentUserId();
        return GoalSummary.from(raceGoalService.getGoal(goalId, userId));
    }

    @Tool(description = "Create a new race goal. Priority is A (main target), B (important), or C (training race). Optional raceId links to an existing entry in the race catalog.")
    public GoalSummary createGoal(
            @ToolParam(description = "Goal title (e.g. 'Sub-3 hour marathon')") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, TRIATHLON, or OTHER") String sport,
            @ToolParam(description = "Priority: A, B, or C") String priority,
            @ToolParam(description = "Race date (YYYY-MM-DD)") LocalDate raceDate,
            @ToolParam(description = "Optional race catalog ID to link") String raceId,
            @ToolParam(description = "Optional notes") String notes) {
        String userId = SecurityUtils.getCurrentUserId();
        RaceGoal goal = new RaceGoal();
        goal.setTitle(title);
        goal.setSport(sport);
        goal.setPriority(priority);
        goal.setRaceDate(raceDate);
        goal.setRaceId(raceId);
        goal.setNotes(notes);
        RaceGoal saved = raceGoalService.createGoal(userId, goal);
        return GoalSummary.from(RaceGoalResponse.from(saved, null));
    }

    @Tool(description = "Update an existing race goal. Pass null for any field you don't want to change. Cannot reassign athleteId.")
    public GoalSummary updateGoal(
            @ToolParam(description = "Goal ID to update") String goalId,
            @ToolParam(description = "New title (null = unchanged)") String title,
            @ToolParam(description = "New sport (null = unchanged)") String sport,
            @ToolParam(description = "New priority A/B/C (null = unchanged)") String priority,
            @ToolParam(description = "New race date (null = unchanged)") LocalDate raceDate,
            @ToolParam(description = "New target time (null = unchanged)") String targetTime,
            @ToolParam(description = "New notes (null = unchanged)") String notes) {
        String userId = SecurityUtils.getCurrentUserId();
        RaceGoalResponse current = raceGoalService.getGoal(goalId, userId);
        RaceGoal update = new RaceGoal();
        update.setTitle(title != null ? title : current.title());
        update.setSport(sport != null ? sport : current.sport());
        update.setPriority(priority != null ? priority : current.priority());
        update.setRaceDate(raceDate != null ? raceDate : current.raceDate());
        update.setTargetTime(targetTime != null ? targetTime : current.targetTime());
        update.setNotes(notes != null ? notes : current.notes());
        update.setDistance(current.distance());
        update.setLocation(current.location());
        update.setRaceId(current.raceId());
        RaceGoal saved = raceGoalService.updateGoal(goalId, userId, update);
        return GoalSummary.from(RaceGoalResponse.from(saved, current.race()));
    }

    @Tool(description = "Delete a race goal by ID.")
    public String deleteGoal(
            @ToolParam(description = "Goal ID to delete") String goalId) {
        String userId = SecurityUtils.getCurrentUserId();
        raceGoalService.deleteGoal(goalId, userId);
        return "Goal deleted.";
    }

    public record GoalSummary(String id, String title, String sport, String raceDate,
                               String priority, String distance, String location,
                               String targetTime, long daysUntil) {
        static GoalSummary from(RaceGoalResponse g) {
            long days = g.raceDate() != null ? LocalDate.now().until(g.raceDate()).getDays() : -1;
            return new GoalSummary(
                    g.id(), g.title(), g.sport(),
                    g.raceDate() != null ? g.raceDate().toString() : null,
                    g.priority(), g.distance(), g.location(), g.targetTime(), days);
        }
    }
}
