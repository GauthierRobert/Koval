package com.koval.trainingplannerbackend.notification;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserRepository userRepository;

    public NotificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        if (!isFirebaseAvailable()) {
            log.debug("Firebase not initialized — skipping notification: {}", title);
            return;
        }

        List<String> allTokens = new ArrayList<>();
        Map<String, List<String>> tokensByUser = new HashMap<>();

        userIds.forEach(userId -> userRepository.findById(userId).ifPresent(user -> {
            if (preferenceType != null && !isPreferenceEnabled(user, preferenceType)) {
                log.debug("User {} has {} preference disabled — skipping", userId, preferenceType);
                return;
            }
            List<String> tokens = user.getFcmTokens();
            if (tokens != null && !tokens.isEmpty()) {
                allTokens.addAll(tokens);
                tokensByUser.put(userId, new ArrayList<>(tokens));
            }
        }));

        if (allTokens.isEmpty()) {
            log.debug("No FCM tokens found for users {} — skipping notification", userIds);
            return;
        }

        sendToTokens(allTokens, title, body, data, tokensByUser);
    }

    private boolean isPreferenceEnabled(User user, String preferenceType) {
        NotificationPreferences prefs = user.getNotificationPreferences();
        if (prefs == null) return true; // default: all enabled
        return switch (preferenceType) {
            case "workoutAssigned" -> prefs.isWorkoutAssigned();
            case "workoutReminder" -> prefs.isWorkoutReminder();
            case "workoutCompletedCoach" -> prefs.isWorkoutCompletedCoach();
            case "clubSessionCreated" -> prefs.isClubSessionCreated();
            case "clubSessionCancelled" -> prefs.isClubSessionCancelled();
            case "waitingListPromoted" -> prefs.isWaitingListPromoted();
            case "planActivated" -> prefs.isPlanActivated();
            case "clubAnnouncement" -> prefs.isClubAnnouncement();
            default -> true;
        };
    }

    private void sendToTokens(List<String> tokens, String title, String body,
                              Map<String, String> data, Map<String, List<String>> tokensByUser) {
        try {
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .addAllTokens(tokens)
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            if (response.getFailureCount() > 0) {
                List<String> staleTokens = new ArrayList<>();
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        MessagingErrorCode errorCode = responses.get(i).getException().getMessagingErrorCode();
                        if (errorCode == MessagingErrorCode.UNREGISTERED
                                || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                            staleTokens.add(tokens.get(i));
                        }
                        log.warn("FCM send failed for token {}: {}", i, responses.get(i).getException().getMessage());
                    }
                }
                if (!staleTokens.isEmpty()) {
                    removeStaleTokens(staleTokens, tokensByUser);
                }
            }

            log.info("FCM notification sent: {} success, {} failure",
                    response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM notification: {}", e.getMessage());
        }
    }

    private void removeStaleTokens(List<String> staleTokens, Map<String, List<String>> tokensByUser) {
        for (var entry : tokensByUser.entrySet()) {
            String userId = entry.getKey();
            List<String> userTokens = entry.getValue();
            List<String> toRemove = userTokens.stream().filter(staleTokens::contains).toList();
            if (!toRemove.isEmpty()) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.getFcmTokens().removeAll(toRemove);
                    userRepository.save(user);
                    log.info("Removed {} stale FCM tokens from user {}", toRemove.size(), userId);
                });
            }
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
        NotificationPreferences prefs = user.getNotificationPreferences();
        return prefs != null ? prefs : new NotificationPreferences();
    }

    public NotificationPreferences updatePreferences(String userId, NotificationPreferences prefs) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setNotificationPreferences(prefs);
        userRepository.save(user);
        return prefs;
    }

    private boolean isFirebaseAvailable() {
        return !FirebaseApp.getApps().isEmpty();
    }
}
