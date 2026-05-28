package com.koval.trainingplannerbackend.training.effectiveness;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSession.BlockSummary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Classifies a completed session into a {@link WorkoutFamily} from its overall IF and block mix.
 *
 * <p>Heuristics, in order of precedence:
 * <ul>
 *   <li>Look at INTERVAL/RAMP blocks: peak target-to-mean ratio determines the high-intensity bucket
 *       (SPRINT &gt; VO2MAX &gt; THRESHOLD &gt; SWEET_SPOT).</li>
 *   <li>If no structured high-intensity blocks, fall back to whole-session IF:
 *       &lt;0.65 RECOVERY, 0.65–0.80 ENDURANCE, 0.80–0.90 TEMPO, 0.90+ SWEET_SPOT.</li>
 *   <li>Sessions with no IF and no block data → MIXED.</li>
 * </ul>
 *
 * <p>We classify on ratios of block target power to the session's mean power (NP proxy), which
 * frees the algorithm from needing each session's FTP at execution time.
 */
@Component
public class WorkoutFamilyClassifier {

    public WorkoutFamily classify(CompletedSession session) {
        if (session == null) return WorkoutFamily.MIXED;
        List<BlockSummary> blocks = session.getBlockSummaries();
        Double sessionIf = session.getIntensityFactor();

        Double peakRatio = peakIntervalRatio(blocks, session.getAvgPower());
        if (peakRatio != null) {
            if (peakRatio >= 1.50) return WorkoutFamily.SPRINT;
            if (peakRatio >= 1.20) return WorkoutFamily.VO2MAX;
            if (peakRatio >= 1.05) return WorkoutFamily.THRESHOLD;
            if (peakRatio >= 0.92) return WorkoutFamily.SWEET_SPOT;
        }

        if (sessionIf != null) {
            if (sessionIf < 0.65) return WorkoutFamily.RECOVERY;
            if (sessionIf < 0.80) return WorkoutFamily.ENDURANCE;
            if (sessionIf < 0.90) return WorkoutFamily.TEMPO;
            return WorkoutFamily.SWEET_SPOT;
        }

        return WorkoutFamily.MIXED;
    }

    private Double peakIntervalRatio(List<BlockSummary> blocks, double avgPower) {
        if (blocks == null || blocks.isEmpty() || avgPower <= 0) return null;
        double peak = 0.0;
        boolean found = false;
        for (BlockSummary b : blocks) {
            if (b == null || b.type() == null) continue;
            String t = b.type();
            if (!"INTERVAL".equalsIgnoreCase(t) && !"RAMP".equalsIgnoreCase(t)) continue;
            double target = b.targetPower();
            if (target <= 0) continue;
            double ratio = target / avgPower;
            if (ratio > peak) peak = ratio;
            found = true;
        }
        return found ? peak : null;
    }
}
