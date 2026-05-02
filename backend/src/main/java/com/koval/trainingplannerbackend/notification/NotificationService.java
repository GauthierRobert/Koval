package com.koval.trainingplannerbackend.notification;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserRepository userRepository;
    private final FirebaseConfig fcmConfig;
    private final RestClient fcmRestClient;
    private final NotificationRepository notificationRepository;

    public NotificationService(UserRepository userRepository,
                               FirebaseConfig fcmConfig,
                               RestClient fcmRestClient,
                               NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.fcmConfig = fcmConfig;
        this.fcmRestClient = fcmRestClient;
        this.notificationRepository = notificationRepository;
    }

    @Async
    public void sendToUser(String userId, String title, String body, Map<String, String> data) {
        sendToUsers(List.of(userId), title, body, data);
    }

    @Async
    public void sendToUsers(List<String> userIds, String title, String body, Map<String, String> data) {
        sendToUsers(userIds, title, body, data, null);
    }

    /**
     * Send a notification to users, optionally filtering by preference type.
     *
     * @param preferenceType if non-null, only sends to users whose NotificationPreferences
     *                       has the corresponding flag enabled. Valid values match
     *                       NotificationPreferences field names.
     */
    @Async
    public void sendToUsers(List<String> userIds, String title, String body,
                            Map<String, String> data, String preferenceType) {
        List<String> allTokens = new ArrayList<>();
        Map<String, List<String>> tokensByUser = new HashMap<>();

        List<User> users = userRepository.findAllById(userIds);
        String type = Optional.ofNullable(data).map(d -> d.getOrDefault("type", preferenceType)).orElse(preferenceType);

        for (User user : users) {
            if (preferenceType != null && !isPreferenceEnabled(user, preferenceType)) {
                log.debug("User {} has {} preference disabled — skipping", user.getId(), preferenceType);
                continue;
            }

            // Persist in-app notification regardless of FCM availability so users
            // see history even when push delivery fails.
            persistNotification(user.getId(), type, title, body, data);

            List<String> tokens = user.getFcmTokens();
            if (tokens != null && !tokens.isEmpty()) {
                allTokens.addAll(tokens);
                tokensByUser.put(user.getId(), new ArrayList<>(tokens));
            }
        }

        if (!fcmConfig.isAvailable()) {
            log.debug("FCM not initialized — persisted only: {}", title);
            return;
        }
        if (allTokens.isEmpty()) {
            log.debug("No FCM tokens found for users {} — persisted only", userIds);
            return;
        }

        sendToTokens(allTokens, title, body, data, tokensByUser);
    }

    private void persistNotification(String userId, String type, String title, String body,
                                     Map<String, String> data) {
        try {
            Notification notification = new Notification(userId, type, title, body, data);
            notificationRepository.save(notification);
        } catch (Exception e) {
            // Persistence must never block FCM dispatch.
            log.warn("Failed to persist notification for user {}: {}", userId, e.getMessage());
        }
    }

    private boolean isPreferenceEnabled(User user, String preferenceType) {
        NotificationPreferences prefs = user.getNotificationPreferences();
        if (prefs == null) return true;
        return switch (preferenceType) {
            case "workoutAssigned" -> prefs.workoutAssigned();
            case "workoutReminder" -> prefs.workoutReminder();
            case "workoutCompletedCoach" -> prefs.workoutCompletedCoach();
            case "clubSessionCreated" -> prefs.clubSessionCreated();
            case "clubSessionCancelled" -> prefs.clubSessionCancelled();
            case "waitingListPromoted" -> prefs.waitingListPromoted();
            case "planActivated" -> prefs.planActivated();
            case "clubAnnouncement" -> prefs.clubAnnouncement();
            default -> true;
        };
    }

    private void sendToTokens(List<String> tokens, String title, String body,
                              Map<String, String> data, Map<String, List<String>> tokensByUser) {
        String accessToken;
        try {
            accessToken = fcmConfig.getAccessToken();
        } catch (IOException e) {
            log.error("Failed to get FCM access token: {}", e.getMessage());
            return;
        }

        String sendUrl = "/v1/projects/" + fcmConfig.getProjectId() + "/messages:send";
        int successCount = 0;
        int failureCount = 0;
        List<String> staleTokens = new ArrayList<>();

        for (String token : tokens) {
            Map<String, Object> message = buildMessage(token, title, body, data);
            try {
                fcmRestClient.post()
                        .uri(sendUrl)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", message))
                        .retrieve()
                        .toBodilessEntity();
                successCount++;
            } catch (org.springframework.web.client.RestClientException e) {
                failureCount++;
                String errorMsg = e.getMessage();
                log.warn("FCM send failed for token: {}", errorMsg);
                if (errorMsg != null && (errorMsg.contains("UNREGISTERED") || errorMsg.contains("INVALID_ARGUMENT"))) {
                    staleTokens.add(token);
                }
            }
        }

        if (!staleTokens.isEmpty()) {
            removeStaleTokens(staleTokens, tokensByUser);
        }

        log.info("FCM notification sent: {} success, {} failure", successCount, failureCount);
    }

    private Map<String, Object> buildMessage(String token, String title, String body, Map<String, String> data) {
        Map<String, Object> notification = Map.of("title", title, "body", body);
        Map<String, Object> message = new HashMap<>();
        message.put("token", token);
        message.put("notification", notification);
        if (data != null && !data.isEmpty()) {
            message.put("data", data);
        }
        return message;
    }

    private void removeStaleTokens(List<String> staleTokens, Map<String, List<String>> tokensByUser) {
        List<String> affectedUserIds = tokensByUser.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(staleTokens::contains))
                .map(Map.Entry::getKey)
                .toList();
        if (affectedUserIds.isEmpty()) return;

        List<User> users = userRepository.findAllById(affectedUserIds);
        List<User> toSave = new ArrayList<>();
        for (User user : users) {
            List<String> userTokens = tokensByUser.get(user.getId());
            List<String> toRemove = userTokens.stream().filter(staleTokens::contains).toList();
            if (!toRemove.isEmpty() && user.getFcmTokens() != null) {
                user.getFcmTokens().removeAll(toRemove);
                toSave.add(user);
                log.info("Removed {} stale FCM tokens from user {}", toRemove.size(), user.getId());
            }
        }
        if (!toSave.isEmpty()) {
            userRepository.saveAll(toSave);
        }
    }

    public void registerToken(String userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getFcmTokens() == null) {
            user.setFcmTokens(new ArrayList<>());
        }
        if (!user.getFcmTokens().contains(token)) {
            user.getFcmTokens().add(token);
            userRepository.save(user);
        }
    }

    public void unregisterToken(String userId, String token) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFcmTokens() != null && user.getFcmTokens().remove(token)) {
                userRepository.save(user);
            }
        });
    }

    public NotificationPreferences getPreferences(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return Optional.ofNullable(user.getNotificationPreferences()).orElseGet(NotificationPreferences::new);
    }

    public NotificationPreferences updatePreferences(String userId, NotificationPreferences prefs) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setNotificationPreferences(prefs);
        userRepository.save(user);
        return prefs;
    }

    // -------- Notification center (in-app history) --------

    public org.springframework.data.domain.Page<Notification> listNotifications(String userId,
                                                                                 org.springframework.data.domain.Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long countUnread(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(String userId, String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (!n.getUserId().equals(userId)) {
                throw new IllegalStateException("Notification does not belong to user");
            }
            if (!n.isRead()) {
                n.setRead(Boolean.TRUE);
                n.setReadAt(Instant.now());
                notificationRepository.save(n);
            }
        });
    }

    public int markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(userId);
        Instant now = Instant.now();
        for (Notification n : unread) {
            n.setRead(true);
            n.setReadAt(now);
        }
        if (!unread.isEmpty()) {
            notificationRepository.saveAll(unread);
        }
        return unread.size();
    }

    public void deleteNotification(String userId, String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (!n.getUserId().equals(userId)) {
                throw new IllegalStateException("Notification does not belong to user");
            }
            notificationRepository.deleteById(notificationId);
        });
    }
}
