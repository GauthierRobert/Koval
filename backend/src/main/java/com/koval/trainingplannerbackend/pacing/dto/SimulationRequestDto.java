package com.koval.trainingplannerbackend.pacing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.koval.trainingplannerbackend.pacing.SimulationRequest;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationRequestDto(
        String id,
        String userId,
        String raceId,
        String goalId,
        String discipline,
        AthleteProfile athleteProfile,
        Integer bikeLoops,
        Integer runLoops,
        String label,
        LocalDateTime createdAt
) {
    public SimulationRequestDto {
        if (bikeLoops == null) bikeLoops = 1;
        if (runLoops == null) runLoops = 1;
    }

    public static SimulationRequestDto from(SimulationRequest entity) {
        return new SimulationRequestDto(
                entity.getId(),
                entity.getUserId(),
                entity.getRaceId(),
                entity.getGoalId(),
                entity.getDiscipline(),
                entity.getAthleteProfile(),
                entity.getBikeLoops(),
                entity.getRunLoops(),
                entity.getLabel(),
                entity.getCreatedAt()
        );
    }
}
