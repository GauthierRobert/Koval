package com.koval.trainingplannerbackend.race.briefing;

import com.koval.trainingplannerbackend.race.DistanceCategory;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.GearChecklist;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.GearGroup;

import java.util.List;
import java.util.Locale;

/**
 * Static gear checklist templates. We intentionally keep this in code (not a
 * collection) because the lists are short, opinionated, and shouldn't grow at
 * runtime — they're the "what every athlete needs at the start line" baseline.
 *
 * <p>Templates are composed: a triathlon brief stitches the swim, bike, run,
 * and general groups together. Athletes will personalize the printed list by
 * hand; the value of the template is making sure nothing obvious is forgotten.
 */
public final class GearChecklists {

    private GearChecklists() {}

    public static GearChecklist forRace(String sport, DistanceCategory category) {
        if (sport == null) return new GearChecklist(List.of(general()));
        String s = sport.toUpperCase(Locale.ROOT);
        return switch (s) {
            case "TRIATHLON" -> new GearChecklist(List.of(
                    swim(),
                    bike(category),
                    run(category),
                    transition(),
                    general()));
            case "RUNNING" -> new GearChecklist(List.of(run(category), general()));
            case "CYCLING" -> new GearChecklist(List.of(bike(category), general()));
            case "SWIMMING" -> new GearChecklist(List.of(swim(), general()));
            default -> new GearChecklist(List.of(general()));
        };
    }

    private static GearGroup swim() {
        return new GearGroup("Swim", List.of(
                "Wetsuit (check water-temp ruling)",
                "Swim cap (race-provided + spare)",
                "Goggles (clear + tinted backup)",
                "Anti-fog spray or saliva trick",
                "Body-glide on neck and underarms",
                "Earplugs (optional)"));
    }

    private static GearGroup bike(DistanceCategory category) {
        boolean longCourse = category == DistanceCategory.TRI_IRONMAN
                || category == DistanceCategory.TRI_HALF
                || category == DistanceCategory.TRI_ULTRA
                || category == DistanceCategory.BIKE_GRAN_FONDO
                || category == DistanceCategory.BIKE_ULTRA;
        return new GearGroup("Bike", concat(
                List.of(
                        "Bike (post-mechanic check, tires inflated)",
                        "Helmet (fastened before mounting!)",
                        "Cycling shoes",
                        "Sunglasses",
                        "Cycling kit / tri-suit",
                        "Bottles filled (2x)",
                        "Bike computer / GPS, charged",
                        "Spare tube + CO2 + tire levers",
                        "Multi-tool",
                        "Race nutrition (gels/bars taped to top tube)"),
                longCourse ? List.of(
                        "Extra spare tube",
                        "Salt tablets",
                        "Sunscreen on shoulders/arms",
                        "Special-needs bag (long-course)") : List.of()));
    }

    private static GearGroup run(DistanceCategory category) {
        boolean longCourse = category == DistanceCategory.RUN_MARATHON
                || category == DistanceCategory.RUN_ULTRA
                || category == DistanceCategory.TRI_HALF
                || category == DistanceCategory.TRI_IRONMAN
                || category == DistanceCategory.TRI_ULTRA;
        return new GearGroup("Run", concat(
                List.of(
                        "Running shoes (broken-in, double-knotted)",
                        "Race kit / tri-suit",
                        "Cap or visor",
                        "Race belt with bib already pinned",
                        "HR strap (optional)",
                        "Anti-chafe stick"),
                longCourse ? List.of(
                        "Hydration vest / handheld",
                        "Gels x N (one per 30 min)",
                        "Spare socks") : List.of()));
    }

    private static GearGroup transition() {
        return new GearGroup("Transition", List.of(
                "Transition bag (sport-specific compartments)",
                "Towel (small, bright color for spotting)",
                "Numbered timing chip",
                "Race-belt with bib",
                "Wristwatch / GPS"));
    }

    private static GearGroup general() {
        return new GearGroup("General", List.of(
                "Photo ID + race confirmation",
                "Cash + card",
                "Pre-race breakfast & coffee",
                "Warm clothes for after",
                "Phone + battery pack",
                "Throwaway warm layer for start line",
                "Sunscreen",
                "Plastic bag for wet kit"));
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        if (b.isEmpty()) return a;
        java.util.ArrayList<T> out = new java.util.ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return List.copyOf(out);
    }
}
