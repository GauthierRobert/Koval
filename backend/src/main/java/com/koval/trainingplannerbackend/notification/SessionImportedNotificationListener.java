package com.koval.trainingplannerbackend.notification;

import com.koval.trainingplannerbackend.club.feed.SessionCompletedEvent;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Pushes a notification to the athlete when a new completed session lands (webhook
 * import or manual upload). Clicking the notification navigates to the session
 * detail page — the frontend maps {@code type=SESSION_IMPORTED} + {@code sessionId}
 * to {@code /history/:sessionId}.
 */
@Component
public class SessionImportedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SessionImportedNotificationListener.class);

    private final NotificationService notificationService;

    public SessionImportedNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    @Async
    public void onSessionCompleted(SessionCompletedEvent event) {
        if (!event.notifyUser()) return;

        CompletedSession session = event.session();
        // Synthetic completions come from the athlete's own COMPLETE button — no need to notify.
        if (Boolean.TRUE.equals(session.getSyntheticCompletion())) return;
        // Same for sessions added through the manual-add UI.
        if (Boolean.TRUE.equals(session.getManuallyCreated())) return;

        try {
            String body = session.getTitle() != null && !session.getTitle().isBlank()
                    ? session.getTitle()
                    : "Your session is ready — tap to see the details";

            notificationService.sendToUsers(
                    List.of(session.getUserId()),
                    "Session imported 🏁",
                    body,
                    Map.of(
                            "type", "SESSION_IMPORTED",
                            "sessionId", session.getId()),
                    "sessionImported");
        } catch (Exception e) {
            log.error("Failed to send session-imported notification for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }
}
