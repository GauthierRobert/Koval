package com.koval.trainingplannerbackend.training.notation;

import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint for the workout notation parser.
 *
 * <pre>
 * POST /api/notation/parse
 * Body:     { "notation": "10min60%-5*(3min105%-2minP)-10minC55%" }
 * Response: { "blocks": [...], "totalDurationSeconds": 2100,
 *             "estimatedIf": 0.82, "estimatedTss": 40 }
 * </pre>
 *
 * The endpoint is stateless and has no side-effects.
 */
@RestController
@RequestMapping("/api/notation")
@CrossOrigin(origins = "*")
@Deprecated(forRemoval = true)
public class NotationController {

    // ── DTOs ──────────────────────────────────────────────────────────────────

    record ParseRequest(String notation) {}

    record ParseResult(
            List<WorkoutBlock> blocks,
            int totalDurationSeconds,
            double estimatedIf,
            int estimatedTss
    ) {}

    // ── Endpoint ──────────────────────────────────────────────────────────────

    @PostMapping("/parse")
    public ResponseEntity<?> parse(@RequestBody ParseRequest req) {
        if (req.notation() == null || req.notation().isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "notation is required"));

        try {
            List<WorkoutBlock> blocks = WorkoutNotationParser.parse(req.notation());
            int totalSec      = totalDuration(blocks);
            double estimatedIf  = computeIF(blocks, totalSec);
            int estimatedTss  = computeTSS(totalSec, estimatedIf);
            return ResponseEntity.ok(new ParseResult(blocks, totalSec, estimatedIf, estimatedTss));
        } catch (WorkoutNotationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Metrics helpers ───────────────────────────────────────────────────────

    /** Sum of durationSeconds for all time-based blocks. */
    private int totalDuration(List<WorkoutBlock> blocks) {
        return blocks.stream()
                .filter(b -> b.durationSeconds() != null)
                .mapToInt(WorkoutBlock::durationSeconds)
                .sum();
    }

    /**
     * Intensity Factor estimate using the normalised-power formula:
     * IF = sqrt( Σ(duration_i × intensity_i²) / totalDuration ) / 100
     */
    private double computeIF(List<WorkoutBlock> blocks, int totalSec) {
        if (totalSec == 0) return 0;
        double sumWeighted = 0;
        for (WorkoutBlock b : blocks) {
            if (b.durationSeconds() == null || b.durationSeconds() == 0) continue;
            double pct = effectivePct(b);
            sumWeighted += b.durationSeconds() * pct * pct;
        }
        double raw = Math.sqrt(sumWeighted / totalSec) / 100.0;
        return Math.round(raw * 100.0) / 100.0;
    }

    /**
     * TSS estimate: (totalHours × IF² × 100)
     */
    private int computeTSS(int totalSec, double estimatedIf) {
        return (int) Math.round((totalSec / 3600.0) * estimatedIf * estimatedIf * 100);
    }

    /** Effective intensity as a raw percentage value (e.g. 85 for 85%). */
    private double effectivePct(WorkoutBlock b) {
        if (b.intensityTarget() != null) return b.intensityTarget();
        if (b.intensityStart() != null && b.intensityEnd() != null)
            return (b.intensityStart() + b.intensityEnd()) / 2.0;
        return switch (b.type()) {
            case WARMUP, COOLDOWN -> 65;
            case FREE             -> 55;
            case PAUSE            -> 0;
            default               -> 70;  // unknown STEADY/INTERVAL without target
        };
    }
}
