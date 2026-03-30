package com.koval.trainingplannerbackend.club.tools;

import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record RecurringTemplateSummary(String id, String title, String sport,
                                       DayOfWeek dayOfWeek, LocalTime timeOfDay,
                                       String location, boolean active,
                                       String clubGroupId, Integer durationMinutes) {

    public static RecurringTemplateSummary from(RecurringSessionTemplate t) {
        return new RecurringTemplateSummary(
                t.getId(), t.getTitle(), t.getSport(),
                t.getDayOfWeek(), t.getTimeOfDay(),
                t.getLocation(), t.isActive(),
                t.getClubGroupId(), t.getDurationMinutes()
        );
    }
}
