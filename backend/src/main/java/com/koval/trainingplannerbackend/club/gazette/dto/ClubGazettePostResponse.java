package com.koval.trainingplannerbackend.club.gazette.dto;

import com.koval.trainingplannerbackend.club.gazette.ClubGazettePost;
import com.koval.trainingplannerbackend.club.gazette.GazettePostType;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ClubGazettePostResponse(
        String id,
        String editionId,
        String authorId,
        String authorDisplayName,
        String authorProfilePicture,
        GazettePostType type,
        String title,
        String content,
        String linkedSessionId,
        String linkedRaceGoalId,
        ClubGazettePost.LinkedSessionSnapshot linkedSessionSnapshot,
        ClubGazettePost.LinkedRaceGoalSnapshot linkedRaceGoalSnapshot,
        List<MediaResponse> photos,
        Integer displayOrder,
        boolean excluded,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ClubGazettePostResponse from(ClubGazettePost p, List<MediaResponse> photos) {
        return new ClubGazettePostResponse(
                p.getId(),
                p.getEditionId(),
                p.getAuthorId(),
                p.getAuthorDisplayName(),
                p.getAuthorProfilePicture(),
                p.getType(),
                p.getTitle(),
                p.getContent(),
                p.getLinkedSessionId(),
                p.getLinkedRaceGoalId(),
                p.getLinkedSessionSnapshot(),
                p.getLinkedRaceGoalSnapshot(),
                photos,
                p.getDisplayOrder(),
                p.isExcluded(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
