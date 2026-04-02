package com.koval.trainingplannerbackend.pacing;

final class PacingUtils {

    private PacingUtils() {}

    static double interpolate(double x, double[] xs, double[] ys) {
        if (x <= xs[0]) return ys[0];
        if (x >= xs[xs.length - 1]) return ys[ys.length - 1];
        for (int i = 0; i < xs.length - 1; i++) {
            if (x <= xs[i + 1]) {
                double ratio = (x - xs[i]) / (xs[i + 1] - xs[i]);
                return ys[i] + ratio * (ys[i + 1] - ys[i]);
            }
        }
        return ys[ys.length - 1];
    }

    static String formatPace(int totalSeconds, String unit) {
        return totalSeconds / 60 + ":" + String.format("%02d", totalSeconds % 60) + " /" + unit;
    }

    static String formatDistKm(double meters) {
        double km = meters / 1000.0;
        if (km >= 10) {
            return (int) Math.round(km) + "km";
        }
        return String.format(java.util.Locale.US, "%.1fkm", km);
    }

    static String fuelSuggestion(String sport, String preference) {
        return switch (sport) {
            case "BIKE" -> switch (preference) {
                case "GELS" -> "Take 1 energy gel (25g carbs) + 200ml water";
                case "DRINK" -> "Drink 300ml carb drink (25-30g carbs)";
                case "SOLID" -> "Eat half energy bar (20-25g carbs) + 200ml water";
                default -> "25-30g carbs: gel, bar piece, or drink mix + water";
            };
            case "RUN" -> switch (preference) {
                case "GELS" -> "Take 1 gel (25g carbs) + water at aid station";
                case "DRINK" -> "200ml sports drink (20-25g carbs) + water";
                case "SOLID" -> "2-3 energy chews (15-20g carbs) + water";
                default -> "20-25g carbs: gel or sports drink + water";
            };
            default -> "20-25g carbs + water";
        };
    }
}
