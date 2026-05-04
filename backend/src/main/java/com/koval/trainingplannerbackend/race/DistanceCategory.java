package com.koval.trainingplannerbackend.race;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controlled vocabulary for race distance categories — the structured filter key
 * that complements the free-form {@link Race#getDistance()} display string.
 *
 * <p>Sport-prefixed names avoid cross-sport collisions in storage and let the AI
 * pick the correct value for the race's sport. The frontend filters on exact
 * enum equality, which is robust regardless of how the display string is phrased
 * (e.g. "Standard", "DO", "Distance Olympique" all resolve to TRI_OLYMPIC).
 */
public enum DistanceCategory {
    // Triathlon — extensive: from federation intro distances (Promo/Discovery) up to Ultra
    TRI_PROMO,           // Promo / Découverte / Discovery — very short intro
    TRI_SUPER_SPRINT,    // ~300-500m / ~10km / ~2.5km
    TRI_SPRINT,          // 750m / 20km / 5km
    TRI_OLYMPIC,         // Olympic / Standard / M Distance / DO — 1.5km / 40km / 10km
    TRI_HALF,            // 70.3 / Half-Ironman / L Distance — 1.9km / 90km / 21.1km
    TRI_IRONMAN,         // Full / 140.6 / XL — 3.8km / 180km / 42.2km
    TRI_ULTRA,           // XXL / Double / Triple Ironman and beyond
    TRI_AQUATHLON,       // Swim + Run only
    TRI_DUATHLON,        // Run + Bike + Run
    TRI_AQUABIKE,        // Swim + Bike (no run)
    TRI_CROSS,           // XTERRA / off-road triathlon

    // Running
    RUN_5K,
    RUN_10K,
    RUN_HALF_MARATHON,   // Semi / 21.1km
    RUN_MARATHON,        // 42.195km
    RUN_ULTRA,           // any distance beyond marathon

    // Cycling
    BIKE_GRAN_FONDO,
    BIKE_MEDIO_FONDO,
    BIKE_TT,             // Time trial
    BIKE_ULTRA,

    // Swimming (open water)
    SWIM_1500M,
    SWIM_5K,
    SWIM_10K,
    SWIM_MARATHON,       // 25km+
    SWIM_ULTRA;

    private static final Map<String, List<DistanceCategory>> BY_SPORT = Map.of(
            "TRIATHLON", List.of(
                    TRI_PROMO, TRI_SUPER_SPRINT, TRI_SPRINT, TRI_OLYMPIC,
                    TRI_HALF, TRI_IRONMAN, TRI_ULTRA,
                    TRI_AQUATHLON, TRI_DUATHLON, TRI_AQUABIKE, TRI_CROSS),
            "RUNNING", List.of(
                    RUN_5K, RUN_10K, RUN_HALF_MARATHON, RUN_MARATHON, RUN_ULTRA),
            "CYCLING", List.of(
                    BIKE_GRAN_FONDO, BIKE_MEDIO_FONDO, BIKE_TT, BIKE_ULTRA),
            "SWIMMING", List.of(
                    SWIM_1500M, SWIM_5K, SWIM_10K, SWIM_MARATHON, SWIM_ULTRA),
            "OTHER", List.of()
    );

    /** Categories that are valid for the given sport (case-insensitive); empty for unknown sports. */
    public static List<DistanceCategory> allowedFor(String sport) {
        if (sport == null) return List.of();
        return BY_SPORT.getOrDefault(sport.toUpperCase(Locale.ROOT), List.of());
    }

    /**
     * Best-effort inference from a free-form distance string + sport. Used to
     * backfill legacy rows and as a safety net when the AI returns only a
     * display string. Returns null when the string doesn't clearly map to any
     * category for the sport.
     */
    public static DistanceCategory infer(String sport, String raw) {
        if (raw == null || raw.isBlank() || sport == null) return null;
        String s = raw.toUpperCase(Locale.ROOT);
        String sp = sport.toUpperCase(Locale.ROOT);

        return switch (sp) {
            case "TRIATHLON" -> inferTriathlon(s);
            case "RUNNING" -> inferRunning(s);
            case "CYCLING" -> inferCycling(s);
            case "SWIMMING" -> inferSwimming(s);
            default -> null;
        };
    }

    private static DistanceCategory inferTriathlon(String s) {
        // Order matters: check most specific patterns first
        if (containsAny(s, "AQUATHLON")) return TRI_AQUATHLON;
        if (containsAny(s, "DUATHLON")) return TRI_DUATHLON;
        if (containsAny(s, "AQUABIKE", "AQUA-BIKE", "AQUA BIKE")) return TRI_AQUABIKE;
        if (containsAny(s, "XTERRA", "CROSS TRI", "CROSS-TRI", "OFF-ROAD")) return TRI_CROSS;
        if (containsAny(s, "PROMO", "DECOUVERTE", "DÉCOUVERTE", "DISCOVERY", "INITIATION")) return TRI_PROMO;
        if (containsAny(s, "SUPER SPRINT", "SUPER-SPRINT", "SUPERSPRINT", "MINI")) return TRI_SUPER_SPRINT;
        if (containsAny(s, "ULTRA", "DOUBLE", "TRIPLE", "DECA", "XXL")) return TRI_ULTRA;
        if (containsAny(s, "IRONMAN", "FULL", "140.6", " XL")) return TRI_IRONMAN;
        if (containsAny(s, "70.3", "HALF", "DEMI", "L DISTANCE", "LONG DISTANCE", "MEDIUM")) return TRI_HALF;
        if (containsAny(s, "OLYMPIC", "OLYMPIQUE", "STANDARD", "M DISTANCE", " DO", "/DO", "OLYMPIC DISTANCE")
                || s.equals("DO") || s.equals("M")) return TRI_OLYMPIC;
        if (containsAny(s, "SPRINT", " S DISTANCE", "/S DISTANCE")) return TRI_SPRINT;
        return null;
    }

    private static DistanceCategory inferRunning(String s) {
        if (containsAny(s, "ULTRA", "100K", "100 K", "50K", "50 K", "100 MILES", "100MI")) return RUN_ULTRA;
        if (containsAny(s, "MARATHON", "42.195", "42K", "42 KM")) {
            if (containsAny(s, "HALF", "SEMI", "DEMI", "21.1", "21K", "21 KM")) return RUN_HALF_MARATHON;
            return RUN_MARATHON;
        }
        if (containsAny(s, "HALF MARATHON", "SEMI", "SEMI-MARATHON", "DEMI-MARATHON", "21.1", "21K", "21 KM"))
            return RUN_HALF_MARATHON;
        if (containsAny(s, "10K", "10 K", "10 KM", "10KM")) return RUN_10K;
        if (containsAny(s, "5K", "5 K", "5 KM", "5KM")) return RUN_5K;
        return null;
    }

    private static DistanceCategory inferCycling(String s) {
        if (containsAny(s, "TIME TRIAL", "TT", "CHRONO")) return BIKE_TT;
        if (containsAny(s, "ULTRA", "RAAM", "TCR")) return BIKE_ULTRA;
        if (containsAny(s, "GRAN FONDO", "GRANFONDO", "GRAN-FONDO")) return BIKE_GRAN_FONDO;
        if (containsAny(s, "MEDIO FONDO", "MEDIOFONDO", "MEDIO-FONDO")) return BIKE_MEDIO_FONDO;
        return null;
    }

    private static DistanceCategory inferSwimming(String s) {
        if (containsAny(s, "ULTRA")) return SWIM_ULTRA;
        if (containsAny(s, "MARATHON", "25K", "25 K")) return SWIM_MARATHON;
        if (containsAny(s, "10K", "10 K", "10 KM")) return SWIM_10K;
        if (containsAny(s, "5K", "5 K", "5 KM")) return SWIM_5K;
        if (containsAny(s, "1500", "1.5K", "1.5 KM")) return SWIM_1500M;
        return null;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
