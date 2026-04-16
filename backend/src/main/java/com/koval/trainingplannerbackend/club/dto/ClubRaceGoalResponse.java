package com.koval.trainingplannerbackend.club.dto;

import java.time.LocalDate;
import java.util.List;

public record ClubRaceGoalResponse(
        String raceId,
        String title,
        String sport,
        LocalDate raceDate,
        String distance,
        String location,
        List<RaceParticipant> participants
) {
    public record RaceParticipant(
            String userId,
            String displayName,
            String profilePicture,
            String priority,
            String targetTime
    ) {}
}
