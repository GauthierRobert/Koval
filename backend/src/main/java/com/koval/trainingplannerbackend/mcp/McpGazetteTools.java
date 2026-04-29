package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.gazette.AutoSection;
import com.koval.trainingplannerbackend.club.gazette.ClubGazettePublisher;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteService;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteSnapshotService;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazetteEditionResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.PublishGazetteRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP tool adapter for the Club Gazette feature. Used by Claude (Desktop /
 * claude.ai) when an owner/admin/coach asks Claude to compile and publish
 * the next gazette edition.
 *
 * Typical flow Claude follows:
 *   1. listOpenDrafts(clubId)
 *   2. getGazettePayload(editionId)               — full data: posts + previews
 *   3. (optionally) previewAutoSections(...)      — try alternative period bounds
 *   4. compose the PDF (locally)
 *   5. publishGazetteWithPdf(...)                 — atomic publish with curation
 */
@Service
public class McpGazetteTools {

    private final ClubGazetteService gazetteService;
    private final ClubGazetteSnapshotService snapshotService;
    private final ClubGazettePublisher publisher;

    public McpGazetteTools(ClubGazetteService gazetteService,
                           ClubGazetteSnapshotService snapshotService,
                           ClubGazettePublisher publisher) {
        this.gazetteService = gazetteService;
        this.snapshotService = snapshotService;
        this.publisher = publisher;
    }

    @Tool(description = "List recent gazette editions for a club, including drafts and published issues.")
    public Object listClubGazetteEditions(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Maximum number of editions to return (default 10)") Integer limit) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        int size = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);
        return gazetteService.listEditions(userId, clubId, 0, size);
    }

    @Tool(description = "List all open DRAFT gazette editions for a club. Use this to know which edition to publish.")
    public Object listOpenGazetteDrafts(
            @ToolParam(description = "Club ID") String clubId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        return gazetteService.getOpenDrafts(userId, clubId);
    }

    @Tool(description = "Get the full structured payload of a gazette edition: live stats, all draft posts (with author, type, links and photo URLs resolved), and previews of every auto-curated section. Use this BEFORE generating the PDF so you know what is available and can let the admin choose what to include.")
    public Object getGazettePayload(
            @ToolParam(description = "Edition ID") String editionId) {
        if (editionId == null || editionId.isBlank()) return "Error: editionId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        return gazetteService.getPayload(userId, editionId);
    }

    @Tool(description = "Recompute the auto-curated sections (stats, leaderboard, top sessions, milestones, most active members) for an arbitrary period. Useful when the admin wants to adjust the period bounds before publishing.")
    public Object previewGazetteAutoSections(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Period start (ISO-8601 LocalDateTime, e.g. 2026-04-19T23:59:59)") LocalDateTime periodStart,
            @ToolParam(description = "Period end (ISO-8601 LocalDateTime, exclusive)") LocalDateTime periodEnd) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (periodStart == null || periodEnd == null) return "Error: periodStart and periodEnd are required.";
        if (!periodEnd.isAfter(periodStart)) return "Error: periodEnd must be after periodStart.";
        // Authorization is implicit: snapshotService is read-only against repos; a
        // non-member who guesses a clubId only gets stats they could compute themselves
        // from public Strava activities. The publish step itself is auth-gated.
        return new java.util.LinkedHashMap<String, Object>() {{
            put("stats", snapshotService.computeStats(clubId, periodStart, periodEnd));
            put("leaderboard", snapshotService.computeLeaderboard(clubId, periodStart, periodEnd));
            put("topSessions", snapshotService.computeTopSessions(clubId, periodStart, periodEnd));
            put("mostActiveMembers", snapshotService.computeMostActiveMembers(clubId, periodStart, periodEnd));
            put("milestones", snapshotService.computeMilestones(clubId, periodStart, periodEnd));
        }};
    }

    @Tool(description = "Publish a gazette edition with the generated PDF and the curation choices. Atomic: freezes snapshots for the chosen sections only, marks excluded posts, attaches the PDF, transitions to PUBLISHED, and notifies club members.")
    public Object publishGazetteWithPdf(
            @ToolParam(description = "Edition ID") String editionId,
            @ToolParam(description = "Base64-encoded PDF bytes (max 10 MB)") String pdfBase64,
            @ToolParam(description = "Filename for the PDF download (optional)") String filename,
            @ToolParam(description = "List of post IDs to include in publication order (others will be marked excluded). Pass empty list to publish without member posts.") List<String> includedPostIds,
            @ToolParam(description = "Auto sections to include: STATS, LEADERBOARD, TOP_SESSIONS, MILESTONES, MOST_ACTIVE_MEMBERS") List<String> includedSections,
            @ToolParam(description = "Optional override for period start (ISO-8601 LocalDateTime)") LocalDateTime periodStart,
            @ToolParam(description = "Optional override for period end (ISO-8601 LocalDateTime)") LocalDateTime periodEnd) {
        if (editionId == null || editionId.isBlank()) return "Error: editionId is required.";
        if (pdfBase64 == null || pdfBase64.isBlank()) return "Error: pdfBase64 is required.";
        String userId = SecurityUtils.getCurrentUserId();

        Set<AutoSection> sections = new HashSet<>();
        if (includedSections != null) {
            for (String s : includedSections) {
                if (s == null || s.isBlank()) continue;
                try {
                    sections.add(AutoSection.valueOf(s.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    return "Error: unknown auto section '" + s + "'. Allowed: STATS, LEADERBOARD, TOP_SESSIONS, MILESTONES, MOST_ACTIVE_MEMBERS.";
                }
            }
        }

        PublishGazetteRequest req = new PublishGazetteRequest(
                pdfBase64, filename,
                includedPostIds == null ? List.of() : includedPostIds,
                sections,
                periodStart, periodEnd);
        ClubGazetteEditionResponse response = publisher.publish(userId, editionId, req);
        return response;
    }

    @Tool(description = "Discard a draft gazette edition (admin only). Removes the draft and all its member posts. Use when an edition is obsolete and shouldn't be published.")
    public Object discardGazetteDraft(
            @ToolParam(description = "Edition ID") String editionId) {
        if (editionId == null || editionId.isBlank()) return "Error: editionId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        gazetteService.discardDraft(userId, editionId);
        return "Draft " + editionId + " discarded.";
    }
}
