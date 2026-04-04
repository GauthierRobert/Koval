package com.koval.trainingplannerbackend.notification;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
