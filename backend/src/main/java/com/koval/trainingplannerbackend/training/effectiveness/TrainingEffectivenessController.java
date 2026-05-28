package com.koval.trainingplannerbackend.training.effectiveness;

import com.koval.trainingplannerbackend.mcp.McpAccessResolver;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Exposes per-athlete training-effectiveness ranking. Athlete sees their own report; coach passes
 * an {@code athleteId} of an athlete they coach (validated by {@link McpAccessResolver}).
 */
@RestController
@RequestMapping("/api/training/effectiveness")
public class TrainingEffectivenessController {

    /** Default lookback when no {@code from} is supplied. */
    private static final int DEFAULT_LOOKBACK_DAYS = 90;

    private final TrainingEffectivenessService service;
    private final McpAccessResolver accessResolver;

    public TrainingEffectivenessController(TrainingEffectivenessService service,
                                           McpAccessResolver accessResolver) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @GetMapping
    public TrainingEffectivenessReport evaluate(
            @RequestParam(required = false) String athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String subjectId = accessResolver.resolve(athleteId).subjectId();
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_LOOKBACK_DAYS);
        return service.evaluate(subjectId, effectiveFrom, effectiveTo);
    }
}
