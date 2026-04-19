package com.koval.trainingplannerbackend.integration.strava;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StravaActivitySyncService {

    private static final Logger log = LoggerFactory.getLogger(StravaActivitySyncService.class);

    private final StravaApiClient stravaApiClient;
    private final StravaActivityMapper mapper = new StravaActivityMapper();
    private final CompletedSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final GridFsOperations gridFsOperations;

    public StravaActivitySyncService(StravaApiClient stravaApiClient,
                                     CompletedSessionRepository sessionRepository,
                                     SessionService sessionService,
                                     UserRepository userRepository,
                                     GridFsOperations gridFsOperations) {
        this.stravaApiClient = stravaApiClient;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.gridFsOperations = gridFsOperations;
    }

    /**
     * Manual history import: imports activities from the last 30 days (max).
     * Deduplicates against existing sessions.
     */
    public SyncResult importHistory(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getStravaRefreshToken() == null || user.getStravaRefreshToken().isBlank()) {
            throw new IllegalStateException("Strava is not connected for this user");
        }

        // Always look back max 30 days, but not before 7 days before account creation
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime earliest = user.getCreatedAt().minusDays(7);
        LocalDateTime after = thirtyDaysAgo.isAfter(earliest) ? thirtyDaysAgo : earliest;
        long afterEpoch = after.toEpochSecond(ZoneOffset.UTC);

        // Fetch activities from Strava
        List<Map<String, Object>> activities = stravaApiClient.fetchActivitiesAfter(user, afterEpoch);

        // Load existing Strava activity IDs for deduplication
        Set<String> existingIds = sessionRepository.findStravaActivityIdsByUserId(userId)
                .stream()
                .map(CompletedSession::getStravaActivityId)
                .collect(Collectors.toSet());

        int skippedDuplicates = 0;
        int skippedErrors = 0;
        List<CompletedSession> importedSessions = new ArrayList<>();

        for (Map<String, Object> activity : activities) {
            String stravaId = String.valueOf(activity.get("id"));

            if (existingIds.contains(stravaId)) {
                skippedDuplicates++;
                continue;
            }

            try {
                CompletedSession session = mapper.map(activity);

                // Fetch laps for per-lap block breakdown (non-fatal)
                boolean deviceWatts = Boolean.TRUE.equals(activity.get("device_watts"));
                List<Map<String, Object>> laps = List.of();
                try {
                    laps = stravaApiClient.fetchLaps(user, stravaId);
                    List<CompletedSession.BlockSummary> lapBlocks = mapper.mapLaps(laps, session.getSportType(), deviceWatts);
                    if (lapBlocks != null) {
                        session.setBlockSummaries(lapBlocks);
                    }
                } catch (RuntimeException lapEx) {
                    log.warn("Failed to fetch laps for Strava activity {}: {}", stravaId, lapEx.getMessage());
                }

                CompletedSession saved = sessionService.saveSession(session, userId);

                // Fetch streams and build FIT file (non-fatal if it fails)
                try {
                    saved = buildAndStoreFit(saved, user, laps);
                } catch (RuntimeException fitEx) {
                    log.warn("Failed to build FIT for Strava activity {}: {}", stravaId, fitEx.getMessage());
                }

                importedSessions.add(saved);
            } catch (RuntimeException e) {
                log.warn("Failed to import Strava activity {}: {}", stravaId, e.getMessage());
                skippedErrors++;
            }
        }

        // Update last sync timestamp
        user.setStravaLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        return new SyncResult(
                activities.size(),
                importedSessions.size(),
                skippedDuplicates,
                skippedErrors,
                importedSessions);
    }

    /**
     * Import a single activity from Strava (called by webhook).
     * Skips if already imported. Does NOT update stravaLastSyncAt.
     */
    public void importSingleActivity(User user, String stravaActivityId) {
        // Check for duplicate
        Set<String> existingIds = sessionRepository.findStravaActivityIdsByUserId(user.getId())
                .stream()
                .map(CompletedSession::getStravaActivityId)
                .collect(Collectors.toSet());

        if (existingIds.contains(stravaActivityId)) {
            log.debug("Strava activity {} already imported, skipping", stravaActivityId);
            return;
        }

        Map<String, Object> activity = stravaApiClient.fetchActivity(user, stravaActivityId);
        if (activity.isEmpty()) {
            log.warn("Strava activity {} returned empty response", stravaActivityId);
            return;
        }

        CompletedSession session = mapper.map(activity);

        boolean deviceWatts = Boolean.TRUE.equals(activity.get("device_watts"));
        List<Map<String, Object>> laps = List.of();
        try {
            laps = stravaApiClient.fetchLaps(user, stravaActivityId);
            List<CompletedSession.BlockSummary> lapBlocks = mapper.mapLaps(laps, session.getSportType(), deviceWatts);
            if (lapBlocks != null) {
                session.setBlockSummaries(lapBlocks);
            }
        } catch (RuntimeException lapEx) {
            log.warn("Failed to fetch laps for Strava activity {}: {}", stravaActivityId, lapEx.getMessage());
        }

        CompletedSession saved = sessionService.saveSession(session, user.getId());

        try {
            buildAndStoreFit(saved, user, laps);
        } catch (RuntimeException fitEx) {
            log.warn("Failed to build FIT for Strava activity {}: {}", stravaActivityId, fitEx.getMessage());
        }
    }

    public SyncStatus getSyncStatus(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean connected = user.getStravaRefreshToken() != null && !user.getStravaRefreshToken().isBlank();
        return new SyncStatus(connected, user.getStravaLastSyncAt(), user.getCreatedAt());
    }

    /**
     * Fetch Strava streams for a session and build+store a FIT file.
     * Returns the updated session with fitFileId set.
     */
    public CompletedSession buildFitForSession(String sessionId, String userId) {
        CompletedSession session = sessionRepository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (session.getStravaActivityId() == null) {
            throw new IllegalStateException("Session is not a Strava import");
        }
        if (session.getFitFileId() != null) {
            return session; // already has FIT
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Map<String, Object>> laps = List.of();
        try {
            laps = stravaApiClient.fetchLaps(user, session.getStravaActivityId());
        } catch (RuntimeException lapEx) {
            log.warn("Failed to fetch laps for Strava activity {}: {}", session.getStravaActivityId(), lapEx.getMessage());
        }

        return buildAndStoreFit(session, user, laps);
    }

    /**
     * Fetch Strava streams, build a FIT binary, store in GridFS, and update the session.
     */
    private CompletedSession buildAndStoreFit(CompletedSession session, User user, List<Map<String, Object>> laps) {
        Map<String, List<? extends Number>> streams =
                stravaApiClient.fetchStreams(user, session.getStravaActivityId());

        if (streams.isEmpty() || !streams.containsKey("time")) {
            throw new IllegalStateException("No stream data available for activity " + session.getStravaActivityId());
        }

        FitFileBuilder builder = new FitFileBuilder();
        byte[] fitBytes = builder.buildFromStreams(
                streams, session.getSportType(), session.getCompletedAt(),
                session.getTotalDurationSeconds(), session.getMovingTimeSeconds(),
                session.getAvgPower(), session.getAvgHR(),
                session.getAvgCadence(), session.getAvgSpeed(),
                laps);

        ObjectId fileId = gridFsOperations.store(
                new ByteArrayInputStream(fitBytes),
                session.getId() + ".fit",
                "application/octet-stream");

        session.setFitFileId(fileId.toHexString());
        return sessionRepository.save(session);
    }

    public record SyncResult(
            int totalFetched,
            int newlyImported,
            int skippedDuplicates,
            int skippedErrors,
            List<CompletedSession> importedSessions) {
    }

    public record SyncStatus(
            boolean stravaConnected,
            LocalDateTime lastSyncAt,
            LocalDateTime memberSince) {
    }
}
