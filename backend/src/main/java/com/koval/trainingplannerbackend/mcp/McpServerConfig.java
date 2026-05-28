package com.koval.trainingplannerbackend.mcp;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Registers all MCP tool adapters as a ToolCallbackProvider for the
 * Spring AI MCP server auto-configuration.
 *
 * <p>Each generated {@link ToolCallback} is wrapped in a {@link SchemaCorrectingToolCallback} to hoist
 * nested {@code $defs} in the tool input schema to the document root — without it, recursive parameter
 * types (e.g. {@code createTraining}'s workout elements) advertise unresolvable {@code #/$defs/...}
 * references that strict MCP clients reject.</p>
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
                                         McpContextTools context,
                                         McpEffectivenessTools effectiveness) {
        ToolCallbackProvider base = MethodToolCallbackProvider.builder()
                .toolObjects(training, scheduling, history, coach, zone, plan, goal, club, race, profile, context, effectiveness)
                .build();

        ToolCallback[] corrected = Arrays.stream(base.getToolCallbacks())
                .map(SchemaCorrectingToolCallback::new)
                .toArray(ToolCallback[]::new);

        return ToolCallbackProvider.from(corrected);
    }
}
