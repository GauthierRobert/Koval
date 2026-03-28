package com.koval.trainingplannerbackend.club.feed;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Document(collection = "club_feed_events")
@CompoundIndexes({
        @CompoundIndex(name = "club_pinned_idx", def = "{'clubId': 1, 'pinned': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "club_session_type_idx", def = "{'clubSessionId': 1, 'type': 1}")
})
public class ClubFeedEvent {

    @Id
    private String id;
    private String clubId;
    private ClubFeedEventType type;
    private boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- SESSION_COMPLETION fields ---
    private String clubSessionId;
    private String sessionTitle;
    private String sessionSport;
    private LocalDateTime sessionScheduledAt;
    private List<CompletionEntry> completions = new ArrayList<>();
    private Set<String> kudosGivenBy = new HashSet<>();
    private List<KudosResult> kudosResults = new ArrayList<>();

    // --- RACE_COMPLETION fields ---
    private String raceGoalId;
    private String raceTitle;
    private LocalDate raceDate;
    private List<RaceCompletionEntry> raceCompletions = new ArrayList<>();

    // --- COACH_ANNOUNCEMENT fields ---
    private String authorId;
    private String authorName;
    private String authorProfilePicture;
    private String announcementContent;

    // --- NEXT_GOAL fields ---
    private String goalTitle;
    private String goalSport;
    private LocalDate goalDate;
    private String goalLocation;
    private List<EngagedAthlete> engagedAthletes = new ArrayList<>();

    // --- Embedded records ---

    public record CompletionEntry(
            String userId,
            String displayName,
            String profilePicture,
            String completedSessionId,
            String stravaActivityId,
            LocalDateTime completedAt) {}

    public record KudosResult(
            String athleteUserId,
            String stravaActivityId,
            String givenByUserId,
            boolean success,
            String errorMessage,
            LocalDateTime attemptedAt) {}

    public record RaceCompletionEntry(
            String userId,
            String displayName,
            String profilePicture,
            String finishTime,
            String stravaActivityId) {}

    public record EngagedAthlete(
            String userId,
            String displayName,
            String profilePicture,
            String priority,
            String targetTime) {}
}
