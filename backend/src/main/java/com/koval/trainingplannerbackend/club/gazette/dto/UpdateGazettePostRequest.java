package com.koval.trainingplannerbackend.club.gazette.dto;

import java.util.List;

public record UpdateGazettePostRequest(
        String title,
        String content,
        String linkedSessionId,
        String linkedRaceGoalId,
        List<String> mediaIds
) {}
