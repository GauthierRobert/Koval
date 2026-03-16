package com.koval.trainingplannerbackend.club.dto;

import java.util.List;

public record ClubRaceGoalResponse(
        Object goal,
        boolean hasUpcomingClubSession,
        List<RaceParticipant> participants
) {
    public record RaceParticipant(String userId, String displayName, String profilePicture) {}
}
