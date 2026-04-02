package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
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
