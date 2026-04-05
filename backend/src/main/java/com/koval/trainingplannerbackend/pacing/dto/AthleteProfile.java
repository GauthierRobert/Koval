package com.koval.trainingplannerbackend.pacing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AthleteProfile(
        Integer ftp,
        Integer weightKg,
        Integer thresholdPaceSec,
        Integer swimCssSec,
        /** Fatigue resistance factor (0.0–1.0). Used in RunPacingService to scale fatigue degradation. Default: 0.5. */
        Double fatigueResistance,
        String nutritionPreference,
        Integer targetPowerWatts,
        Integer targetPaceSecPerKm,
        Integer swimDistanceM,
        Integer targetSwimPaceSecPer100m,
        String bikeType
) {
    /**
     * Merge user defaults into this profile, filling any null fields.
     */
    public AthleteProfile withDefaults(Integer defaultFtp, Integer defaultWeightKg,
                                       Integer defaultThresholdPace, Integer defaultSwimCss) {
        return new AthleteProfile(
                ftp != null ? ftp : defaultFtp,
                weightKg != null ? weightKg : defaultWeightKg,
                thresholdPaceSec != null ? thresholdPaceSec : defaultThresholdPace,
                swimCssSec != null ? swimCssSec : defaultSwimCss,
                fatigueResistance != null ? fatigueResistance : 0.5,
                nutritionPreference != null ? nutritionPreference : "MIXED",
                targetPowerWatts,
                targetPaceSecPerKm,
                swimDistanceM,
                targetSwimPaceSecPer100m,
                bikeType != null ? bikeType : "ROAD_AERO"
        );
    }
}
