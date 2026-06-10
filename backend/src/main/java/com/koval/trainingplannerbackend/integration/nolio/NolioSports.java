package com.koval.trainingplannerbackend.integration.nolio;

import com.koval.trainingplannerbackend.training.model.SportType;

import java.util.Set;

/**
 * Two-way mapping between our {@link SportType} and Nolio's sport ids
 * (https://github.com/NolioApp/NolioAPI-Documentation/wiki/Training-Object#sport-map).
 */
public final class NolioSports {

    /** Road, MTB, virtual ride, track, CX. */
    private static final Set<Integer> CYCLING_IDS = Set.of(14, 15, 18, 35, 36);
    /** Running, treadmill, trail, OCR. */
    private static final Set<Integer> RUNNING_IDS = Set.of(2, 24, 52, 53);
    private static final Set<Integer> SWIMMING_IDS = Set.of(19);

    private NolioSports() {
    }

    /** Nolio sport id for an outgoing push; BRICK has no equivalent → "Other" (12). */
    public static int toNolioSportId(SportType sport) {
        if (sport == null) return 14;
        return switch (sport) {
            case CYCLING -> 14;  // Road cycling
            case RUNNING -> 2;   // Running
            case SWIMMING -> 19; // Swimming
            case BRICK -> 12;    // Other
        };
    }

    /** Our sport for an incoming Nolio object, or null when we don't support it. */
    public static SportType fromNolioSportId(int sportId) {
        if (CYCLING_IDS.contains(sportId)) return SportType.CYCLING;
        if (RUNNING_IDS.contains(sportId)) return SportType.RUNNING;
        if (SWIMMING_IDS.contains(sportId)) return SportType.SWIMMING;
        return null;
    }
}
