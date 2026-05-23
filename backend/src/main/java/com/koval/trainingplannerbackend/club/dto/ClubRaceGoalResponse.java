package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.race.DistanceCategory;

import java.util.List;

public record ClubRaceGoalResponse(
        String raceId,
        String title,
        String sport,
        String raceDate,   // YYYY-MM-DD; sourced from the linked race's scheduledDate
        String distance,
        DistanceCategory distanceCategory,
        String location,
        boolean past,      // true when the race's scheduledDate is before today
        List<RaceParticipant> participants
) {
    public record RaceParticipant(
            String userId,
            String displayName,
            String profilePicture,
            String priority,
            String targetTime,
            String completedSessionId  // viewer's own RACE session for this race, when one exists
    ) {}
}
