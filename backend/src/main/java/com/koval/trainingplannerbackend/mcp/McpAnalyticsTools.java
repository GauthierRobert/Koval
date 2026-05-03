package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.mcp.render.MarkdownChartRenderer;
import com.koval.trainingplannerbackend.mcp.render.MarkdownChartRenderer.Row;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService.PmcDataPoint;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSession.BlockSummary;
import com.koval.trainingplannerbackend.training.history.SessionService;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService.FriResult;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService.VolumeEntry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool adapter that returns ready-to-render Markdown — bar charts, sparklines,
 * tables and weekly grids — for common training analytics views. Each tool is a
 * thin wrapper around an existing analytics/power-curve service plus the pure-Java
 * {@link MarkdownChartRenderer}. No new business logic lives here.
 */
@Service
public class McpAnalyticsTools {

    private final PowerCurveService powerCurveService;
    private final AnalyticsService analyticsService;
    private final SessionService sessionService;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final TrainingService trainingService;

    public McpAnalyticsTools(PowerCurveService powerCurveService,
                             AnalyticsService analyticsService,
                             SessionService sessionService,
                             ScheduledWorkoutService scheduledWorkoutService,
                             TrainingService trainingService) {
        this.powerCurveService = powerCurveService;
        this.analyticsService = analyticsService;
        this.sessionService = sessionService;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.trainingService = trainingService;
    }

    @Tool(description = "Render a Markdown power curve report (bar chart of best mean-maximal watts at each standard duration: 5s, 15s, 30s, 1m, 2m, 5m, 10m, 20m, 30m, 1h, 1.5h, 2h) for the user's cycling sessions in a date range. Output is a fenced code block with unicode block bars — drop directly into a chat reply.")
    public String renderPowerCurveReport(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        Map<Integer, Double> curve = powerCurveService.getBestPowerCurve(userId, from, to);
        if (curve.isEmpty()) {
            return "_No cycling power data in this range._";
        }
        List<Row> rows = curve.entrySet().stream()
                .map(e -> new Row(formatDuration(e.getKey()), e.getValue()))
                .toList();
        return MarkdownChartRenderer.barChart(
                "Best power curve " + from + " → " + to, rows, 30, "W");
    }

    @Tool(description = "Render a Markdown PMC (Performance Management Chart) report: a unicode sparkline of CTL (fitness) over the date range plus a table with current CTL/ATL/TSB and a one-line interpretation of the user's form (fresh, neutral, fatigued, overreached).")
    public String renderPmcReport(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        List<PmcDataPoint> pmc = analyticsService.generatePmc(userId, from, to);
        if (pmc.isEmpty()) {
            return "_No training history in this range._";
        }
        List<Double> ctl = pmc.stream().map(PmcDataPoint::ctl).toList();
        List<Double> atl = pmc.stream().map(PmcDataPoint::atl).toList();
        List<Double> tsb = pmc.stream().map(PmcDataPoint::tsb).toList();
        PmcDataPoint last = pmc.get(pmc.size() - 1);

        StringBuilder sb = new StringBuilder();
        sb.append("**PMC ").append(from).append(" → ").append(to).append("**\n\n");
        sb.append("```\n");
        sb.append("CTL  ").append(MarkdownChartRenderer.sparkline(ctl)).append("\n");
        sb.append("ATL  ").append(MarkdownChartRenderer.sparkline(atl)).append("\n");
        sb.append("TSB  ").append(MarkdownChartRenderer.sparkline(tsb)).append("\n");
        sb.append("```\n\n");

        LinkedHashMap<String, String> table = new LinkedHashMap<>();
        table.put("Current CTL (fitness)", MarkdownChartRenderer.formatValue(last.ctl()));
        table.put("Current ATL (fatigue)", MarkdownChartRenderer.formatValue(last.atl()));
        table.put("Current TSB (form)", MarkdownChartRenderer.formatValue(last.tsb()));
        table.put("Form interpretation", interpretTsb(last.tsb()));
        sb.append(MarkdownChartRenderer.kvTable(null, table));
        return sb.toString();
    }

    @Tool(description = "Render a Markdown training volume report: a bar chart of total TSS per week or month over the date range. Use groupBy='week' or 'month'.")
    public String renderVolumeReport(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to,
            @ToolParam(description = "Aggregation period: 'week' or 'month'") String groupBy) {
        String userId = SecurityUtils.getCurrentUserId();
        List<VolumeEntry> entries = powerCurveService.computeVolume(userId, from, to, groupBy);
        if (entries.isEmpty()) {
            return "_No sessions in this range._";
        }
        List<Row> rows = entries.stream()
                .map(v -> new Row(v.period(), v.totalTss()))
                .toList();
        return MarkdownChartRenderer.barChart(
                "Training volume " + from + " → " + to + " (by " + groupBy + ")",
                rows, 30, "TSS");
    }

    @Tool(description = "Render the user's scheduled workouts for a given week as a 7-day Markdown calendar grid (Mon-Sun). Each day shows the resolved training title plus the workout's status (PENDING / COMPLETED / SKIPPED).")
    public String renderWeekSchedule(
            @ToolParam(description = "Monday of the week to render (YYYY-MM-DD). If a non-Monday is given, the renderer rolls back to the preceding Monday.") LocalDate weekStart) {
        String userId = SecurityUtils.getCurrentUserId();
        LocalDate monday = weekStart.minusDays((weekStart.getDayOfWeek().getValue() - 1));
        LocalDate sunday = monday.plusDays(6);
        List<ScheduledWorkout> workouts = scheduledWorkoutService.getAthleteSchedule(userId, monday, sunday);

        Map<DayOfWeek, List<String>> entries = new HashMap<>();
        workouts.stream()
                .filter(sw -> sw.getScheduledDate() != null)
                .forEach(sw -> {
                    String title = resolveTitle(sw.getTrainingId());
                    String marker = sw.getStatus() == ScheduleStatus.COMPLETED ? "✔ "
                            : sw.getStatus() == ScheduleStatus.SKIPPED ? "✗ " : "○ ";
                    entries.computeIfAbsent(sw.getScheduledDate().getDayOfWeek(), k -> new ArrayList<>())
                            .add(marker + title);
                });
        StringBuilder sb = new StringBuilder();
        sb.append("**Week of ").append(monday).append("**\n\n");
        sb.append(MarkdownChartRenderer.weekGrid(monday, entries));
        return sb.toString();
    }

    @Tool(description = "Render a single completed session as a Markdown card: title, sport, duration, average power/HR, TSS/IF, RPE, total distance, the per-block breakdown, and (for cycling) a power curve bar chart from that session's FIT file. The power curve is computed lazily on first call.")
    public String renderSessionSummary(
            @ToolParam(description = "Completed session ID") String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession s = sessionService.getSession(userId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(s.getTitle() != null ? s.getTitle() : "Untitled session").append("\n\n");

        LinkedHashMap<String, String> overview = new LinkedHashMap<>();
        overview.put("Sport", s.getSportType() != null ? s.getSportType() : "—");
        overview.put("Date", s.getCompletedAt() != null ? s.getCompletedAt().toLocalDate().toString() : "—");
        overview.put("Duration", formatDuration(s.getTotalDurationSeconds()));
        if (s.getMovingTimeSeconds() != null) overview.put("Moving time", formatDuration(s.getMovingTimeSeconds()));
        overview.put("Avg power", s.getAvgPower() > 0 ? MarkdownChartRenderer.formatValue(s.getAvgPower()) + " W" : "—");
        overview.put("Avg HR", s.getAvgHR() > 0 ? MarkdownChartRenderer.formatValue(s.getAvgHR()) + " bpm" : "—");
        overview.put("TSS", s.getTss() != null ? MarkdownChartRenderer.formatValue(s.getTss()) : "—");
        overview.put("IF", s.getIntensityFactor() != null ? MarkdownChartRenderer.formatValue(s.getIntensityFactor()) : "—");
        overview.put("RPE", s.getRpe() != null ? s.getRpe() + "/10" : "—");
        overview.put("Distance", s.getTotalDistance() != null
                ? MarkdownChartRenderer.formatValue(s.getTotalDistance() / 1000.0) + " km" : "—");
        sb.append(MarkdownChartRenderer.kvTable(null, overview)).append("\n");

        if (s.getBlockSummaries() != null && !s.getBlockSummaries().isEmpty()) {
            sb.append("**Blocks**\n\n");
            sb.append("| # | Type | Duration | Target W | Actual W | Avg HR |\n");
            sb.append("|---|---|---|---|---|---|\n");
            int i = 1;
            for (BlockSummary b : s.getBlockSummaries()) {
                sb.append("| ").append(i++).append(" | ")
                        .append(b.type() != null ? b.type() : "—").append(" | ")
                        .append(formatDuration(b.durationSeconds())).append(" | ")
                        .append(b.targetPower() > 0 ? MarkdownChartRenderer.formatValue(b.targetPower()) : "—").append(" | ")
                        .append(b.actualPower() > 0 ? MarkdownChartRenderer.formatValue(b.actualPower()) : "—").append(" | ")
                        .append(b.actualHR() > 0 ? MarkdownChartRenderer.formatValue(b.actualHR()) : "—").append(" |\n");
            }
            sb.append("\n");
        }

        Map<Integer, Double> curve = powerCurveService.getSessionPowerCurve(sessionId, userId);
        if (!curve.isEmpty()) {
            List<Row> rows = curve.entrySet().stream()
                    .map(e -> new Row(formatDuration(e.getKey()), e.getValue()))
                    .toList();
            sb.append(MarkdownChartRenderer.barChart("Session power curve", rows, 30, "W"));
        }

        return sb.toString();
    }

    @Tool(description = "Render a Fatigue Resistance Index (FRI) report: the ratio of 60-minute best power to 5-minute best power from the power curve. FRI indicates how well an athlete sustains power over long durations — crucial for long-course triathlon. Typical range: 0.65-0.85. Values above 0.80 indicate excellent fatigue resistance. Requires maximal efforts at both durations (rejects flat/Z2-only curves).")
    public String renderFriReport(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        Map<Integer, Double> curve = powerCurveService.getBestPowerCurve(userId, from, to);
        FriResult fri = PowerCurveService.computeFri(curve);
        if (fri == null) {
            Double p5 = curve.get(300);
            Double p60 = curve.get(3600);
            if (p60 == null || p60 <= 0) {
                return "_Cannot compute Fatigue Resistance Index: no 60-minute power data in this range. " +
                       "Ensure the date range includes rides of at least 60 minutes with power data._";
            }
            return "_Cannot compute Fatigue Resistance Index: the power curve is too flat (no maximal short efforts). " +
                   "FRI requires genuine hard efforts at 5 minutes, not just Zone-2 riding._";
        }

        LinkedHashMap<String, String> table = new LinkedHashMap<>();
        table.put("FRI (60min / 5min)", MarkdownChartRenderer.formatValue(fri.fri()));
        table.put("5-min best power", MarkdownChartRenderer.formatValue(fri.power5min()) + " W");
        table.put("60-min best power", MarkdownChartRenderer.formatValue(fri.power60min()) + " W");
        table.put("Rating", interpretFri(fri.level()));
        return MarkdownChartRenderer.kvTable(
                "Fatigue Resistance Index " + from + " → " + to, table);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private String resolveTitle(String trainingId) {
        if (trainingId == null) return "Unknown";
        try {
            return trainingService.getTrainingById(trainingId).getTitle();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) {
            int m = seconds / 60;
            int s = seconds % 60;
            return s == 0 ? m + "m" : m + "m" + s + "s";
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return m == 0 ? h + "h" : h + "h" + m + "m";
    }

    private static String interpretTsb(double tsb) {
        if (tsb < -30) return "Overreached / high fatigue — recovery needed";
        if (tsb < -10) return "Building / training load productive";
        if (tsb < 5) return "Neutral";
        if (tsb < 25) return "Fresh / race-ready";
        return "Detrained — increase load";
    }

    private static String interpretFri(String level) {
        return switch (level) {
            case "excellent" -> "Excellent — elite fatigue resistance, strong for long-course triathlon";
            case "good" -> "Good — solid endurance profile, well-suited for half-distance";
            case "moderate" -> "Moderate — typical trained cyclist, more long rides will improve this";
            case "developing" -> "Developing — sprint-oriented profile, prioritize longer steady rides";
            default -> level;
        };
    }
}
