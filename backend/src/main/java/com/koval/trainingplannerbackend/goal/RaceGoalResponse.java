package com.koval.trainingplannerbackend.goal;

import com.koval.trainingplannerbackend.race.Race;

import java.time.LocalDateTime;

public record RaceGoalResponse(
        String id,
        String athleteId,
        String title,
        String sport,
        String priority,
        String distance,
        String location,
        String notes,
        String targetTime,
        LocalDateTime createdAt,
        String raceId,
        Race race) {

    public static RaceGoalResponse from(RaceGoal goal, Race race) {
        return new RaceGoalResponse(
                goal.getId(), goal.getAthleteId(), goal.getTitle(), goal.getSport(),
                goal.getPriority(), goal.getDistance(), goal.getLocation(),
                goal.getNotes(), goal.getTargetTime(), goal.getCreatedAt(), goal.getRaceId(), race);
    }

    /** Convenience accessor — date is sourced from the linked race's scheduledDate. */
    public String raceDate() {
        return race != null ? race.getScheduledDate() : null;
    }
}
