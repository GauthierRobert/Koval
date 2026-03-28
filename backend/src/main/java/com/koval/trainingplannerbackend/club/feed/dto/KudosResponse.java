package com.koval.trainingplannerbackend.club.feed.dto;

import java.util.List;

public record KudosResponse(
        List<KudosResultDto> results,
        int successCount,
        int failCount) {

    public record KudosResultDto(
            String athleteName,
            String stravaActivityId,
            boolean success,
            String error) {}
}
