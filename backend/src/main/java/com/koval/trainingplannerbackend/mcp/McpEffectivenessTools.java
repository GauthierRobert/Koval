package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.training.effectiveness.TrainingEffectivenessReport;
import com.koval.trainingplannerbackend.training.effectiveness.TrainingEffectivenessService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * MCP tool adapter for the training-effectiveness evaluator.
 */
@Service
public class McpEffectivenessTools {

    private static final int DEFAULT_LOOKBACK_DAYS = 90;

    private final TrainingEffectivenessService service;
    private final McpAccessResolver accessResolver;

    public McpEffectivenessTools(TrainingEffectivenessService service,
                                 McpAccessResolver accessResolver) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @Tool(description = "Evaluate which workout types are producing the most fitness return for the athlete. "
            + "Splits the window in half, compares the best mean-maximal power curves between halves, and "
            + "attributes the 20-minute power gain to each workout family (RECOVERY, ENDURANCE, TEMPO, "
            + "SWEET_SPOT, THRESHOLD, VO2MAX, SPRINT, MIXED) in proportion to its TSS share. Returns a "
            + "ranked list with estimated watts gained per 1000 TSS, plus session count, average IF, "
            + "alignment and RPE per family. Use this when the user asks 'what training is working for me' "
            + "or a coach asks which sessions move the needle. Heuristic, not causal — flag low session "
            + "counts. Omit athleteId for your own report; pass a coached athlete's id (requires COACH "
            + "role and a coaching relationship). Defaults to the last 90 days if from/to are omitted.")
    public TrainingEffectivenessReport evaluateTrainingResponse(
            @ToolParam(required = false, description = "Start date inclusive (YYYY-MM-DD). Defaults to 90 days before 'to'.") LocalDate from,
            @ToolParam(required = false, description = "End date inclusive (YYYY-MM-DD). Defaults to today.") LocalDate to,
            @ToolParam(required = false, description = "Coached athlete's user ID. Omit/null for your own report.") String athleteId) {
        String subjectId = accessResolver.resolve(athleteId).subjectId();
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_LOOKBACK_DAYS);
        return service.evaluate(subjectId, effectiveFrom, effectiveTo);
    }
}
