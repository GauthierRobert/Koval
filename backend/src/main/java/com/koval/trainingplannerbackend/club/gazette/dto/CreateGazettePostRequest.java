package com.koval.trainingplannerbackend.club.gazette.dto;

import com.koval.trainingplannerbackend.club.gazette.GazettePostType;

import java.util.List;

public record CreateGazettePostRequest(
        GazettePostType type,
        String title,
        String content,
        String linkedSessionId,
        String linkedRaceGoalId,
        List<String> mediaIds
) {}
