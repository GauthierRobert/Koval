package com.koval.trainingplannerbackend.notification;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
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
}
