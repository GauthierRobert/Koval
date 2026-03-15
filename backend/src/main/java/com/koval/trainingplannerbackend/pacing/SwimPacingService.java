package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import org.springframework.stereotype.Service;

@Service
class SwimPacingService {

    private static final double[] DIST_POINTS  = {750, 1500, 1900, 3800};
    private static final double[] PACE_FACTORS = {0.97, 1.02, 1.05, 1.08};

    /**
     * Build swim summary from athlete profile. Returns null if swim data is missing
     * (no swim distance or CSS) — caller uses this to omit the swim leg from the plan.
     */
    PacingSummary buildSummary(AthleteProfile profile) {
        if (profile.swimDistanceM() == null || profile.swimDistanceM() <= 0
                || profile.swimCssSec() == null || profile.swimCssSec() <= 0) {
            return null;
        }

        int swimDist = profile.swimDistanceM();
        int cssPer100m = profile.swimCssSec();

        int targetPace;
        String targetBasis;
        Integer computedTarget;

        if (profile.targetSwimPaceSecPer100m() != null && profile.targetSwimPaceSecPer100m() > 0) {
            targetPace = profile.targetSwimPaceSecPer100m();
            targetBasis = null;
            computedTarget = null;
        } else {
            double factor = PacingUtils.interpolate(swimDist, DIST_POINTS, PACE_FACTORS);
            targetPace = (int) Math.round(cssPer100m * factor);
            computedTarget = targetPace;
            targetBasis = PacingUtils.formatPace(targetPace, "100m") + " for " + swimDist + "m swim";
        }

        double estimatedTime = (swimDist / 100.0) * targetPace;

        return new PacingSummary(
                swimDist,
                Math.round(estimatedTime * 10.0) / 10.0,
                null,
                PacingUtils.formatPace(targetPace, "100m"),
                0,
                "Hydrate well before start",
                targetBasis,
                computedTarget
        );
    }
}
