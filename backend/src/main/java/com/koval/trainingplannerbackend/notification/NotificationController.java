package com.koval.trainingplannerbackend.notification;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
