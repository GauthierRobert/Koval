package com.koval.trainingplannerbackend.club.gazette;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Document(collection = "club_gazette_editions")
@CompoundIndexes({
        @CompoundIndex(name = "club_period_idx", def = "{'clubId': 1, 'periodStart': -1}"),
        @CompoundIndex(name = "club_status_idx", def = "{'clubId': 1, 'status': 1}")
})
public class ClubGazetteEdition {

    @Id
    private String id;

    @Indexed
    private String clubId;

    private int editionNumber;

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private GazetteStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private String publishedByUserId;

    // Curation choices captured at publish time
    private Set<AutoSection> includedSections = new HashSet<>();

    // Snapshots — populated only when the corresponding AutoSection is included
    private WeeklyStatsSnapshot statsSnapshot;
    private List<LeaderboardSnapshot> leaderboardSnapshot = new ArrayList<>();
    private List<TopSessionSnapshot> topSessions = new ArrayList<>();
    private List<MemberHighlightSnapshot> mostActiveMembers = new ArrayList<>();
    private List<MilestoneSnapshot> milestones = new ArrayList<>();

    // Engagement on the published edition itself
    private int viewCount;
    private Set<String> readBy = new HashSet<>();
    private List<CommentEntry> comments = new ArrayList<>();

    // PDF (kept out of JSON; downloaded via dedicated endpoint)
    @JsonIgnore
    private byte[] pdfData;
    private String pdfFileName;
    private LocalDateTime pdfGeneratedAt;
    private Long pdfSizeBytes;

    // ── Embedded records ─────────────────────────────────────────────────────

    public record WeeklyStatsSnapshot(
            double swimKm, double bikeKm, double runKm,
            int sessionCount, double totalHours, double totalTss,
            int memberCount, int clubSessionsCount, double attendanceRate) {}

    public record LeaderboardSnapshot(
            int rank, String userId, String displayName, String profilePicture,
            double tss, int sessionCount) {}

    public record TopSessionSnapshot(
            String clubSessionId, String title, String sport,
            LocalDate date, int participantCount,
            List<String> participantNames) {}

    public record MemberHighlightSnapshot(
            String userId, String displayName, String profilePicture,
            double hours, int sessions, long tss) {}

    public record MilestoneSnapshot(
            String type,        // PR_DISTANCE, PR_DURATION, FIRST_SESSION, ANNIVERSARY, RACE_FINISHED, ...
            String userId, String displayName, String profilePicture,
            String description) {}

    public record CommentEntry(
            String id, String userId, String displayName, String profilePicture,
            String content, LocalDateTime createdAt) {}
}
