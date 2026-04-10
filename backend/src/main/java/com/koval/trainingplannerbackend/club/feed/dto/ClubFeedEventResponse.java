package com.koval.trainingplannerbackend.club.feed.dto;

import com.koval.trainingplannerbackend.club.feed.ClubFeedEvent;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ClubFeedEventResponse(
        String id,
        ClubFeedEventType type,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // SESSION_COMPLETION
        String clubSessionId,
        String sessionTitle,
        String sessionSport,
        LocalDateTime sessionScheduledAt,
        List<ClubFeedEvent.CompletionEntry> completions,
        Set<String> kudosGivenBy,
        // RACE_COMPLETION
        String raceGoalId,
        String raceTitle,
        LocalDate raceDate,
        List<ClubFeedEvent.RaceCompletionEntry> raceCompletions,
        // COACH_ANNOUNCEMENT
        String authorId,
        String authorName,
        String authorProfilePicture,
        String announcementContent,
        // NEXT_GOAL
        String goalTitle,
        String goalSport,
        LocalDate goalDate,
        String goalLocation,
        List<ClubFeedEvent.EngagedAthlete> engagedAthletes,
        // COMMENTS
        List<ClubFeedEvent.CommentEntry> comments) {

    public static ClubFeedEventResponse from(ClubFeedEvent e) {
        return new ClubFeedEventResponse(
                e.getId(), e.getType(), Boolean.TRUE.equals(e.getPinned()), e.getCreatedAt(), e.getUpdatedAt(),
                e.getClubSessionId(), e.getSessionTitle(), e.getSessionSport(), e.getSessionScheduledAt(),
                e.getCompletions(), e.getKudosGivenBy(),
                e.getRaceGoalId(), e.getRaceTitle(), e.getRaceDate(), e.getRaceCompletions(),
                e.getAuthorId(), e.getAuthorName(), e.getAuthorProfilePicture(), e.getAnnouncementContent(),
                e.getGoalTitle(), e.getGoalSport(), e.getGoalDate(), e.getGoalLocation(), e.getEngagedAthletes(),
                e.getComments());
    }
}
