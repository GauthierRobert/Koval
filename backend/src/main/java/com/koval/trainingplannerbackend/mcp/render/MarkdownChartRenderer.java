package com.koval.trainingplannerbackend.mcp.render;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (fraction < 0) fraction = 0;
        if (fraction > 1) fraction = 1;
        double total = fraction * maxWidth;
        int full = (int) Math.floor(total);
        double remainder = total - full;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < full; i++) sb.append('█');
        if (full < maxWidth && remainder > 0) {
            int idx = (int) Math.floor(remainder * BLOCKS.length);
            if (idx >= BLOCKS.length) idx = BLOCKS.length - 1;
            sb.append(BLOCKS[idx]);
        }
        return sb.toString();
    }

    /** Inline unicode sparkline of values, e.g. {@code ▁▃▅▇█▆▄}. */
    public static String sparkline(List<? extends Number> series) {
        if (series == null || series.isEmpty()) return "";
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Number n : series) {
            if (n == null) continue;
            double v = n.doubleValue();
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (Double.isInfinite(min) || max == min) return repeat(SPARK[0], series.size());
        StringBuilder sb = new StringBuilder(series.size());
        double range = max - min;
        for (Number n : series) {
            if (n == null) {
                sb.append(' ');
                continue;
            }
            double frac = (n.doubleValue() - min) / range;
            int idx = (int) Math.round(frac * (SPARK.length - 1));
            if (idx < 0) idx = 0;
            if (idx >= SPARK.length) idx = SPARK.length - 1;
            sb.append(SPARK[idx]);
        }
        return sb.toString();
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
        for (Map.Entry<String, String> e : rows.entrySet()) {
            sb.append("| ").append(escape(e.getKey())).append(" | ")
                    .append(escape(e.getValue() == null ? "—" : e.getValue())).append(" |\n");
        }
        return sb.toString();
    }

    /**
     * Render a Mon-Sun weekly grid as a markdown table. Each cell shows the entries for
     * that day (joined with {@code <br>}). Empty days show a dash.
     */
    public static String weekGrid(LocalDate weekStart, Map<DayOfWeek, List<String>> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (DayOfWeek d : DayOfWeek.values()) {
            LocalDate date = weekStart.plusDays(d.getValue() - 1);
            sb.append(date.format(HEADER_FMT)).append(" | ");
        }
        sb.append("\n|");
        for (int i = 0; i < 7; i++) sb.append("---|");
        sb.append("\n| ");
        for (DayOfWeek d : DayOfWeek.values()) {
            List<String> dayEntries = entries == null ? null : entries.get(d);
            if (dayEntries == null || dayEntries.isEmpty()) {
                sb.append("—");
            } else {
                sb.append(String.join("<br>", dayEntries));
            }
            sb.append(" | ");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Format a numeric value with at most one decimal place, dropping trailing zeros. */
    public static String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        sb.append(s);
        for (int i = s.length(); i < width; i++) sb.append(' ');
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        char[] arr = new char[n];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
