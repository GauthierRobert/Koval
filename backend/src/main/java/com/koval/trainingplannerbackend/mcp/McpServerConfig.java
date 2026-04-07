package com.koval.trainingplannerbackend.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers all MCP tool adapters as a ToolCallbackProvider for the
 * Spring AI MCP server auto-configuration.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpTools(McpTrainingTools training,
                                         McpSchedulingTools scheduling,
                                         McpHistoryTools history,
                                         McpCoachTools coach,
                                         McpZoneTools zone,
                                         McpPlanTools plan,
                                         McpGoalTools goal,
                                         McpClubTools club,
                                         McpRaceTools race,
                                         McpProfileTools profile,
                                         McpAnalyticsTools analytics) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(training, scheduling, history, coach, zone, plan, goal, club, race, profile, analytics)
                .build();
    }
}
