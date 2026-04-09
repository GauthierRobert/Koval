package com.koval.trainingplannerbackend.plan;

import java.time.LocalDate;
import java.util.List;

public record ActivatePlanRequest(
        LocalDate startDate,
        List<String> athleteIds
) {}
