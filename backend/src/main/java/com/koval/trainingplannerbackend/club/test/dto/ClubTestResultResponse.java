package com.koval.trainingplannerbackend.club.test.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ClubTestResultResponse(
        String id,
        String iterationId,
        String testId,
        String clubId,
        String athleteId,
        String athleteDisplayName,
        String athleteProfilePicture,
        Map<String, SegmentResultValueDto> segmentResults,
        Map<String, Double> computedReferences,
        List<AppliedReferenceUpdateDto> appliedUpdates,
        Integer rank,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String recordedBy
) {}
