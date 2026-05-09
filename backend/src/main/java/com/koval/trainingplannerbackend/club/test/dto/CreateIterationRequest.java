package com.koval.trainingplannerbackend.club.test.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateIterationRequest(
        @NotBlank String label,
        LocalDate startDate,
        LocalDate endDate,
        boolean closeCurrent
) {}
