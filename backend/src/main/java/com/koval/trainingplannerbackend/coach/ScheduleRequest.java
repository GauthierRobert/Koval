package com.koval.trainingplannerbackend.coach;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ScheduleRequest(
    String trainingId,
    @NotNull LocalDate scheduledDate,
    String notes
) {}
