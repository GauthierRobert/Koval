package com.koval.trainingplannerbackend.ai.tools.goal;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.goal.RaceGoalResponse;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * AI-facing tool service for Race Goal operations.
 * Delegates to RaceGoalService for all business logic.
 */
@Service
public class GoalToolService {

    private final RaceGoalService raceGoalService;

    public GoalToolService(RaceGoalService raceGoalService) {
        this.raceGoalService = raceGoalService;
    }

    @Tool(description = "List race goals for a user (by date ascending). The race date is sourced from the linked race entry.")
    public List<GoalSummary> listGoals(ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        return raceGoalService.getGoalsForAthlete(userId)
                .stream()
                .map(GoalSummary::from)
                .toList();
    }

    @Tool(description = "Delete a race goal.")
    public String deleteGoal(
            @ToolParam(description = "Goal ID") String goalId,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        raceGoalService.deleteGoal(goalId, userId);
        return "Goal deleted.";
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
            long daysUntil,
            String raceId
    ) {
        static GoalSummary from(RaceGoalResponse g) {
            String raceDate = g.raceDate();
            long days = -1;
            if (raceDate != null) {
                try {
                    days = LocalDate.now().until(LocalDate.parse(raceDate)).getDays();
                } catch (DateTimeParseException ignored) {}
            }
            return new GoalSummary(
                    g.id(), g.title(), g.sport(),
                    raceDate, g.priority(),
                    g.distance(), g.location(), g.targetTime(), g.notes(),
                    days, g.raceId());
        }
    }
}
