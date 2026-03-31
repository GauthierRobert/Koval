package com.koval.trainingplannerbackend.club.dto;

import java.util.List;
import java.util.Map;

public record ClubExtendedStatsResponse(
        // Weekly volume totals
        double totalSwimKm,
        double totalBikeKm,
        double totalRunKm,
        int totalSessions,
        int memberCount,
        double totalDurationHours,
        double totalTss,

        // Attendance
        double attendanceRate,
        int clubSessionsThisWeek,
        List<RecurringTemplateAttendance> recurringAttendance,

        // Training load
        Map<String, Double> sportDistribution,
        double avgTssPerMember,
        List<WeeklyTrend> weeklyTrends,

        // Member highlights
        List<MemberHighlight> mostActiveMembers
) {
    public record RecurringTemplateAttendance(
            String templateId, String templateTitle, String sport,
            String dayOfWeek, String timeOfDay,
            String clubGroupId, String clubGroupName,
            Integer maxParticipants, int eligibleCount,
            List<WeekAttendance> weeks,
            List<AthletePresence> athleteGrid
    ) {}

    public record WeekAttendance(
            String weekLabel, String sessionId,
            boolean cancelled, int participantCount,
            int eligibleCount, double fillPercent
    ) {}

    public record AthletePresence(
            String userId, String displayName, String profilePicture,
            List<Boolean> weekPresence
    ) {}

    public record WeeklyTrend(String weekLabel, double totalTss, double totalHours,
                              int sessionCount, double attendanceRate) {}

    public record MemberHighlight(String userId, String displayName, String profilePicture,
                                  double totalHours, int sessionCount, double totalTss) {}
}
