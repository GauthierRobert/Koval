package com.koval.trainingplannerbackend.integration.garmin;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GarminActivitySyncService {

    private static final Logger log = LoggerFactory.getLogger(GarminActivitySyncService.class);

    private final GarminApiClient garminApiClient;
    private final GarminActivityMapper mapper = new GarminActivityMapper();
    private final CompletedSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    public GarminActivitySyncService(GarminApiClient garminApiClient,
                                      CompletedSessionRepository sessionRepository,
                                      SessionService sessionService,
                                      UserRepository userRepository) {
        this.garminApiClient = garminApiClient;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
    }

    public SyncResult importHistory(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getGarminAccessToken() == null || user.getGarminAccessToken().isBlank()) {
            throw new IllegalStateException("Garmin is not connected for this user");
        }

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long startEpoch = thirtyDaysAgo.toEpochSecond(ZoneOffset.UTC);
        long endEpoch = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        List<Map<String, Object>> activities = garminApiClient.fetchActivities(
                user.getGarminAccessToken(), user.getGarminAccessTokenSecret(), startEpoch, endEpoch);

        Set<String> existingIds = sessionRepository.findGarminActivityIdsByUserId(userId)
                .stream()
                .map(CompletedSession::getGarminActivityId)
                .collect(Collectors.toSet());

        int skippedDuplicates = 0;
        int skippedErrors = 0;
        List<CompletedSession> importedSessions = new ArrayList<>();

        for (Map<String, Object> activity : activities) {
            String garminId = String.valueOf(activity.get("activityId"));

            if (existingIds.contains(garminId)) {
                skippedDuplicates++;
                continue;
            }

            try {
                CompletedSession session = mapper.map(activity);
                CompletedSession saved = sessionService.saveSession(session, userId);
                importedSessions.add(saved);
            } catch (Exception e) {
                log.warn("Failed to import Garmin activity {}: {}", garminId, e.getMessage());
                skippedErrors++;
            }
        }

        user.setGarminLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        return new SyncResult(activities.size(), importedSessions.size(), skippedDuplicates, skippedErrors);
    }

    public void importSingleActivity(User user, String activityId) {
        Set<String> existingIds = sessionRepository.findGarminActivityIdsByUserId(user.getId())
                .stream()
                .map(CompletedSession::getGarminActivityId)
                .collect(Collectors.toSet());

        if (existingIds.contains(activityId)) {
            log.debug("Garmin activity {} already imported", activityId);
            return;
        }

        Map<String, Object> activity = garminApiClient.fetchActivity(
                user.getGarminAccessToken(), user.getGarminAccessTokenSecret(), activityId);

        if (activity.isEmpty()) return;

        CompletedSession session = mapper.map(activity);
        sessionService.saveSession(session, user.getId());
    }

    public record SyncResult(int totalFetched, int newlyImported, int skippedDuplicates, int skippedErrors) {}
}
