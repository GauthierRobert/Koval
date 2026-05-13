package com.koval.trainingplannerbackend.mcp.render;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pure-Java markdown chart rendering helpers used by MCP analytics tools.
 *
 * <p>Outputs unicode block bar charts, sparklines, key/value tables and weekly calendar
 * grids — all rendered in Markdown so they display natively in Claude clients with no
 * server-side image generation.
 */
public final class MarkdownChartRenderer {

    private static final char[] BLOCKS = {'▏', '▎', '▍', '▌', '▋', '▊', '▉', '█'};
    private static final char[] SPARK = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    private static final DateTimeFormatter HEADER_FMT = DateTimeFormatter.ofPattern("EEE d MMM");

    private MarkdownChartRenderer() {}

    public record Row(String label, double value) {}

    /**
     * Render a horizontal bar chart of {@code rows} as a fenced code block.
     * The widest bar uses {@code maxWidth} block characters; the rest scale linearly.
     *
     * @param title    title rendered above the chart
     * @param rows     ordered list of (label, value) pairs
     * @param maxWidth width in characters of the largest bar (e.g. 30)
     * @param unit     unit appended to each value (e.g. "W", "TSS"); may be null
     */
    public static String barChart(String title, List<Row> rows, int maxWidth, String unit) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) sb.append("**").append(title).append("**\n\n");
        if (rows == null || rows.isEmpty()) {
            sb.append("_No data._\n");
            return sb.toString();
        }
        int labelWidth = rows.stream().mapToInt(r -> r.label() == null ? 0 : r.label().length()).max().orElse(0);
        double max = rows.stream().mapToDouble(Row::value).max().orElse(0);
        if (max <= 0) {
            sb.append("_No values._\n");
            return sb.toString();
        }
        String suffix = unit == null ? "" : " " + unit;
        sb.append("```\n");
        for (Row r : rows) {
            String label = padRight(r.label() == null ? "" : r.label(), labelWidth);
            sb.append(label).append("  ");
            sb.append(bar(r.value() / max, maxWidth));
            sb.append("  ").append(formatValue(r.value())).append(suffix).append('\n');
        }
        sb.append("```\n");
        return sb.toString();
    }

    /** Build a single bar of {@code maxWidth} characters representing fraction in [0,1]. */
    private static String bar(double fraction, int maxWidth) {
        double clamped = Math.clamp(fraction, 0.0, 1.0);
        double total = clamped * maxWidth;
        int full = (int) Math.floor(total);
        double remainder = total - full;
        String body = "█".repeat(full);
        if (full < maxWidth && remainder > 0) {
            int idx = Math.min((int) Math.floor(remainder * BLOCKS.length), BLOCKS.length - 1);
            return body + BLOCKS[idx];
        }
        return body;
    }

    /** Inline unicode sparkline of values, e.g. {@code ▁▃▅▇█▆▄}. */
    public static String sparkline(List<? extends Number> series) {
        if (series == null || series.isEmpty()) return "";
        DoubleSummaryStatistics stats = series.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .summaryStatistics();
        if (stats.getCount() == 0 || stats.getMax() == stats.getMin()) {
            return String.valueOf(SPARK[0]).repeat(series.size());
        }
        double min = stats.getMin();
        double range = stats.getMax() - min;
        return series.stream()
                .map(n -> {
                    if (n == null) return " ";
                    int idx = Math.clamp(
                            (int) Math.round((n.doubleValue() - min) / range * (SPARK.length - 1)),
                            0, SPARK.length - 1);
                    return String.valueOf(SPARK[idx]);
                })
                .collect(Collectors.joining());
    }

    /** Render an ordered key/value map as a 2-column markdown table. */
    public static String kvTable(String title, LinkedHashMap<String, String> rows) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) sb.append("**").append(title).append("**\n\n");
        if (rows == null || rows.isEmpty()) {
            sb.append("_No data._\n");
            return sb.toString();
        }
        sb.append("| Field | Value |\n|---|---|\n");
        String body = rows.entrySet().stream()
                .map(e -> "| " + escape(e.getKey()) + " | "
                        + escape(e.getValue() == null ? "—" : e.getValue()) + " |")
                .collect(Collectors.joining("\n", "", "\n"));
        sb.append(body);
        return sb.toString();
    }

    /**
     * Render a Mon-Sun weekly grid as a markdown table. Each cell shows the entries for
     * that day (joined with {@code <br>}). Empty days show a dash.
     */
    public static String weekGrid(LocalDate weekStart, Map<DayOfWeek, List<String>> entries) {
        String header = Stream.of(DayOfWeek.values())
                .map(d -> weekStart.plusDays(d.getValue() - 1).format(HEADER_FMT))
                .collect(Collectors.joining(" | ", "| ", " | "));
        String separator = "|" + "---|".repeat(DayOfWeek.values().length);
        String row = Stream.of(DayOfWeek.values())
                .map(d -> {
                    List<String> dayEntries = entries == null ? null : entries.get(d);
                    return (dayEntries == null || dayEntries.isEmpty())
                            ? "—"
                            : String.join("<br>", dayEntries);
                })
                .collect(Collectors.joining(" | ", "| ", " | "));
        return header + "\n" + separator + "\n" + row + "\n";
    }

    /** Format a numeric value with at most one decimal place, dropping trailing zeros. */
    public static String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static String padRight(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
