package com.koval.trainingplannerbackend.coach;

import java.time.LocalDate;

public record ScheduleSummary(
        String id,
        String trainingId,
        String trainingTitle,
        LocalDate scheduledDate,
        ScheduleStatus status,
        String notes
) {
    public static ScheduleSummary from(ScheduledWorkout sw, String trainingTitle) {
        return new ScheduleSummary(
                sw.getId(),
                sw.getTrainingId(),
                trainingTitle,
                sw.getScheduledDate(),
                sw.getStatus(),
                sw.getNotes()
        );
    }
}
