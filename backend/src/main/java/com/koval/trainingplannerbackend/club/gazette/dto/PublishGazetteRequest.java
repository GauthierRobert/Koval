package com.koval.trainingplannerbackend.club.gazette.dto;

import com.koval.trainingplannerbackend.club.gazette.AutoSection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Payload sent by Claude (via MCP) when publishing a gazette. Carries the
 * curation choices made interactively with the admin: which posts to include,
 * which auto-curated sections to compute, and (optionally) overrides for the
 * default period bounds.
 */
public record PublishGazetteRequest(
        String pdfBase64,
        String pdfFilename,
        List<String> includedPostIds,
        Set<AutoSection> includedSections,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
) {}
