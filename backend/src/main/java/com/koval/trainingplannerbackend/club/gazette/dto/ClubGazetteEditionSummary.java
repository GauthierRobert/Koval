package com.koval.trainingplannerbackend.club.gazette.dto;

import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition;
import com.koval.trainingplannerbackend.club.gazette.GazetteStatus;

import java.time.LocalDateTime;

/** Lightweight row for the list view. */
public record ClubGazetteEditionSummary(
        String id,
        int editionNumber,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        GazetteStatus status,
        LocalDateTime publishedAt,
        boolean hasPdf
) {
    public static ClubGazetteEditionSummary from(ClubGazetteEdition e) {
        return new ClubGazetteEditionSummary(
                e.getId(),
                e.getEditionNumber(),
                e.getPeriodStart(),
                e.getPeriodEnd(),
                e.getStatus(),
                e.getPublishedAt(),
                e.getPdfData() != null && e.getPdfData().length > 0);
    }
}
