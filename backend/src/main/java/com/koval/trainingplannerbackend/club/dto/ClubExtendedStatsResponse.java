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
        List<SessionAttendance> recentSessionAttendance,
        List<AttendanceRanking> topAttendees,

        // Training load
        Map<String, Double> sportDistribution,
        double avgTssPerMember,
        List<WeeklyTrend> weeklyTrends,

        // Member highlights
        List<MemberHighlight> mostActiveMembers
) {
    public record SessionAttendance(String sessionId, String title, String scheduledAt,
                                    int participantCount, int memberCount, String sport,
                                    String responsibleCoachName) {}

    public record AttendanceRanking(String userId, String displayName, String profilePicture,
                                    int sessionsAttended, int totalSessions, double attendanceRate) {}

    public record WeeklyTrend(String weekLabel, double totalTss, double totalHours,
                              int sessionCount, double attendanceRate) {}

    public record MemberHighlight(String userId, String displayName, String profilePicture,
                                  double totalHours, int sessionCount, double totalTss) {}
}
