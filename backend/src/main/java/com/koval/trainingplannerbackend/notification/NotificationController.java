package com.koval.trainingplannerbackend.notification;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final Environment env;

    public NotificationController(NotificationService notificationService, Environment env) {
        this.notificationService = notificationService;
        this.env = env;
    }

    record TokenRequest(String token) {}

    @PostMapping("/register-token")
    public ResponseEntity<Void> registerToken(@RequestBody TokenRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.registerToken(userId, request.token());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/unregister-token")
    public ResponseEntity<Void> unregisterToken(@RequestBody TokenRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.unregisterToken(userId, request.token());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferences> getPreferences() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferences> updatePreferences(@RequestBody NotificationPreferences prefs) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(notificationService.updatePreferences(userId, prefs));
    }

    @PostMapping("/test")
    public ResponseEntity<Void> sendTestNotification() {
        if (env.matchesProfiles("prod")) {
            return ResponseEntity.notFound().build();
        }
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.sendToUser(userId, "Test Notification", "This is a test notification from Koval!", Map.of("type", "test"));
        return ResponseEntity.ok().build();
    }

    // -------- Notification center --------

    public record NotificationListResponse(List<Notification> notifications, long total, int page, int size) {}

    @GetMapping
    public ResponseEntity<NotificationListResponse> list(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        Page<Notification> result = notificationService.listNotifications(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(new NotificationListResponse(
                result.getContent(), result.getTotalElements(), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.markRead(userId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        String userId = SecurityUtils.getCurrentUserId();
        int count = notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("marked", count));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.deleteNotification(userId, id);
        return ResponseEntity.ok().build();
    }
}
