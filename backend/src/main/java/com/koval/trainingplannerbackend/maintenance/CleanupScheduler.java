package com.koval.trainingplannerbackend.maintenance;

import com.koval.trainingplannerbackend.ai.ChatHistoryRepository;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.config.audit.AuditLog;
import com.koval.trainingplannerbackend.notification.NotificationRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily maintenance jobs that prune stale documents to keep the working set small.
 * Cohabits with {@code ScheduledNotificationJob} and {@code RecurringSessionScheduler}.
 */
@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final ChatHistoryRepository chatHistoryRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Value("${cleanup.chatHistory.retentionDays:90}")
    private int chatHistoryRetentionDays;

    @Value("${cleanup.fcmToken.retentionDays:60}")
    private int fcmTokenRetentionDays;

    @Value("${cleanup.notifications.readRetentionDays:30}")
    private int readNotificationRetentionDays;

    public CleanupScheduler(ChatHistoryRepository chatHistoryRepository,
                            ScheduledWorkoutRepository scheduledWorkoutRepository,
                            TrainingRepository trainingRepository,
                            UserRepository userRepository,
                            NotificationRepository notificationRepository) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(cron = "${cleanup.chatHistory.cron:0 0 3 * * *}")
    @AuditLog(action = "CLEANUP_CHAT_HISTORY")
    public void cleanOldChatHistories() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(chatHistoryRetentionDays);
        long deleted = chatHistoryRepository.deleteByLastUpdatedAtBefore(cutoff);
        log.info("CleanupScheduler: deleted {} chat histories older than {}", deleted, cutoff);
    }

    @Scheduled(cron = "${cleanup.scheduledWorkouts.cron:0 15 3 * * *}")
    @AuditLog(action = "CLEANUP_ORPHANED_SCHEDULED_WORKOUTS")
    public void cleanOrphanedScheduledWorkouts() {
        // Walk all scheduled workouts and drop those whose training no longer exists.
        List<ScheduledWorkout> all = scheduledWorkoutRepository.findAll();
        List<String> toDelete = new ArrayList<>();
        for (ScheduledWorkout sw : all) {
            String trainingId = sw.getTrainingId();
            if (trainingId == null || !trainingRepository.existsById(trainingId)) {
                toDelete.add(sw.getId());
            }
        }
        if (!toDelete.isEmpty()) {
            scheduledWorkoutRepository.deleteAllById(toDelete);
        }
        log.info("CleanupScheduler: deleted {} orphaned scheduled workouts (scanned {})",
                toDelete.size(), all.size());
    }

    @Scheduled(cron = "${cleanup.fcmToken.cron:0 30 3 * * *}")
    @AuditLog(action = "CLEANUP_FCM_TOKENS")
    public void cleanExpiredFcmTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(fcmTokenRetentionDays);
        List<User> stale = userRepository.findUsersWithStaleFcmTokens(cutoff);
        int cleared = 0;
        for (User u : stale) {
            if (u.getFcmTokens() != null && !u.getFcmTokens().isEmpty()) {
                cleared += u.getFcmTokens().size();
                u.setFcmTokens(new ArrayList<>());
            }
        }
        if (!stale.isEmpty()) {
            userRepository.saveAll(stale);
        }
        log.info("CleanupScheduler: cleared {} FCM tokens from {} inactive users (cutoff {})",
                cleared, stale.size(), cutoff);
    }

    @Scheduled(cron = "${cleanup.notifications.cron:0 45 3 * * *}")
    @AuditLog(action = "CLEANUP_READ_NOTIFICATIONS")
    public void cleanOldReadNotifications() {
        Instant cutoff = Instant.now().minus(readNotificationRetentionDays, ChronoUnit.DAYS);
        long deleted = notificationRepository.deleteByReadTrueAndReadAtBefore(cutoff);
        log.info("CleanupScheduler: deleted {} read notifications older than {}", deleted, cutoff);
    }
}
