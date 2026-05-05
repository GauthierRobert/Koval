package com.koval.trainingplannerbackend.club.test.formula;

/** Static helpers exposed as SpEL functions inside test formulas. Kept side-effect-free and pure-numeric. */
public final class FormulaHelpers {

    private FormulaHelpers() {}

    public static double pow(double base, double exp) { return Math.pow(base, exp); }

    public static double sqrt(double value) { return Math.sqrt(value); }

    public static double abs(double value) { return Math.abs(value); }

    public static double min(double a, double b) { return Math.min(a, b); }

    public static double max(double a, double b) { return Math.max(a, b); }

    public static double secondsPerKm(double timeSeconds, double distanceMeters) {
        if (distanceMeters <= 0) return 0;
        return timeSeconds * 1000.0 / distanceMeters;
    }

    public static double secondsPer100m(double timeSeconds, double distanceMeters) {
        if (distanceMeters <= 0) return 0;
        return timeSeconds * 100.0 / distanceMeters;
    }

    public static double pacePerKm(double timeSeconds, double distanceMeters) {
        return secondsPerKm(timeSeconds, distanceMeters);
    }

    public static double round(double value) { return Math.round(value); }
}
