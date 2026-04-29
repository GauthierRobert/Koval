package com.koval.trainingplannerbackend.club.gazette.dto;

import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition;
import com.koval.trainingplannerbackend.club.gazette.GazetteStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rich payload returned to Claude (via MCP) so it has everything needed to
 * render a gazette PDF: edition meta, all draft posts (with photos resolved),
 * and live previews of every auto-curated section. Claude then chooses what
 * to include and calls {@code publishGazetteWithPdf}.
 */
public record ClubGazettePayloadResponse(
        String editionId,
        String clubId,
        String clubName,
        int editionNumber,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        GazetteStatus status,
        List<ClubGazettePostResponse> posts,
        ClubGazetteEdition.WeeklyStatsSnapshot statsPreview,
        List<ClubGazetteEdition.LeaderboardSnapshot> leaderboardPreview,
        List<ClubGazetteEdition.TopSessionSnapshot> topSessionsPreview,
        List<ClubGazetteEdition.MemberHighlightSnapshot> mostActiveMembersPreview,
        List<ClubGazetteEdition.MilestoneSnapshot> milestonesPreview
) {}
