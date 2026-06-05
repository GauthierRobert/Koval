package com.koval.trainingplannerbackend.auth;

/**
 * Published when a user's threshold reference values change (FTP, functional threshold pace,
 * critical swim speed). Listeners backfill metrics that could not be computed while the
 * reference was missing — e.g. TSS/IF on historic sessions imported before FTP was set.
 */
public record ThresholdReferenceChangedEvent(String userId) {}
