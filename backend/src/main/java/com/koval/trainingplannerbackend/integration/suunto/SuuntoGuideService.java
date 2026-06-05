package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Pushes Koval planned workouts to the athlete's watch as SuuntoPlus guides. The guide's
 * {@code externalId} is derived deterministically from the schedule source id, so update and
 * delete address the same guide without extra bookkeeping.
 */
@Service
public class SuuntoGuideService {

    private static final Logger log = LoggerFactory.getLogger(SuuntoGuideService.class);
    private static final String EXTERNAL_ID_PREFIX = "koval-";

    private final SuuntoApiClient apiClient;
    private final SuuntoOAuthService oauthService;
    private final SuuntoGuideBuilder builder = new SuuntoGuideBuilder();
    private final String guidesOwner;

    public SuuntoGuideService(SuuntoApiClient apiClient,
                              SuuntoOAuthService oauthService,
                              @Value("${suunto.guides-owner:Koval}") String guidesOwner) {
        this.apiClient = apiClient;
        this.oauthService = oauthService;
        this.guidesOwner = guidesOwner;
    }

    /** Create the guide on Suunto. Returns its external id for the sync framework. */
    public Optional<String> pushTraining(User athlete, Training training, LocalDate scheduledDate,
                                         String sourceId) {
        requireSuuntoConnected(athlete);
        String accessToken = oauthService.ensureValidToken(athlete);
        String externalId = externalId(sourceId);
        Map<String, Object> guide = builder.build(training, athlete.getFtp(), guidesOwner,
                externalId, scheduledDate);
        return apiClient.pushGuide(accessToken, guide);
    }

    /** Replace the existing guide; falls back to a fresh push when the update is rejected. */
    public Optional<String> updateTraining(User athlete, Training training, LocalDate scheduledDate,
                                           String sourceId, String externalRef) {
        requireSuuntoConnected(athlete);
        if (externalRef == null || externalRef.isBlank()) {
            return pushTraining(athlete, training, scheduledDate, sourceId);
        }
        String accessToken = oauthService.ensureValidToken(athlete);
        Map<String, Object> guide = builder.build(training, athlete.getFtp(), guidesOwner,
                externalRef, scheduledDate);
        try {
            apiClient.updateGuide(accessToken, externalRef, guide);
            return Optional.of(externalRef);
        } catch (RuntimeException e) {
            log.warn("Suunto guide update failed for {}, re-pushing: {}", externalRef, e.getMessage());
            return pushTraining(athlete, training, scheduledDate, sourceId);
        }
    }

    /** Best-effort delete of the guide. */
    public void deleteTraining(User athlete, String externalRef) {
        if (athlete.getSuuntoAccessToken() == null || externalRef == null || externalRef.isBlank()) return;
        String accessToken = oauthService.ensureValidToken(athlete);
        apiClient.deleteGuide(accessToken, externalRef);
    }

    private static String externalId(String sourceId) {
        return EXTERNAL_ID_PREFIX + (sourceId != null ? sourceId : "workout");
    }

    private static void requireSuuntoConnected(User athlete) {
        if (athlete.getSuuntoAccessToken() == null || athlete.getSuuntoUserId() == null) {
            throw new IllegalStateException("Athlete has not connected Suunto");
        }
    }
}
