package com.koval.trainingplannerbackend.club.gazette.dto;

import com.koval.trainingplannerbackend.club.gazette.AutoSection;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition;
import com.koval.trainingplannerbackend.club.gazette.GazetteStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Full edition payload returned to a club member viewing one edition. */
public record ClubGazetteEditionResponse(
        String id,
        String clubId,
        int editionNumber,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        GazetteStatus status,
        LocalDateTime publishedAt,
        String publishedByUserId,
        Set<AutoSection> includedSections,
        ClubGazetteEdition.WeeklyStatsSnapshot statsSnapshot,
        List<ClubGazetteEdition.LeaderboardSnapshot> leaderboardSnapshot,
        List<ClubGazetteEdition.TopSessionSnapshot> topSessions,
        List<ClubGazetteEdition.MemberHighlightSnapshot> mostActiveMembers,
        List<ClubGazetteEdition.MilestoneSnapshot> milestones,
        int viewCount,
        List<ClubGazetteEdition.CommentEntry> comments,
        boolean hasPdf
) {
    public static ClubGazetteEditionResponse from(ClubGazetteEdition e) {
        return new ClubGazetteEditionResponse(
                e.getId(), e.getClubId(), e.getEditionNumber(),
                e.getPeriodStart(), e.getPeriodEnd(),
                e.getStatus(), e.getPublishedAt(), e.getPublishedByUserId(),
                e.getIncludedSections(),
                e.getStatsSnapshot(),
                e.getLeaderboardSnapshot(),
                e.getTopSessions(),
                e.getMostActiveMembers(),
                e.getMilestones(),
                e.getViewCount(),
                e.getComments(),
                e.getPdfData() != null && e.getPdfData().length > 0);
    }
}
