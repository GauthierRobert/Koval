package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    @Tool(description = "List race goals for a user (by date ascending).")
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
        static GoalSummary from(RaceGoal g) {
            long days = g.getRaceDate() != null ? LocalDate.now().until(g.getRaceDate()).getDays() : -1;
            return new GoalSummary(
                    g.getId(), g.getTitle(), g.getSport(),
                    g.getRaceDate() != null ? g.getRaceDate().toString() : null, g.getPriority(),
                    g.getDistance(), g.getLocation(), g.getTargetTime(), g.getNotes(),
                    days, g.getRaceId());
        }
    }
}
