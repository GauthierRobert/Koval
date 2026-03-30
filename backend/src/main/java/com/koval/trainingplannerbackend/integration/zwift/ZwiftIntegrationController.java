package com.koval.trainingplannerbackend.integration.zwift;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/integration/zwift")
@CrossOrigin(origins = "*")
public class ZwiftIntegrationController {

    private final ZwiftAuthService authService;
    private final ZwiftActivitySyncService syncService;
    private final ZwiftWorkoutService workoutService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TrainingService trainingService;

    public ZwiftIntegrationController(ZwiftAuthService authService,
                                       ZwiftActivitySyncService syncService,
                                       ZwiftWorkoutService workoutService,
                                       UserService userService,
                                       UserRepository userRepository,
                                       TrainingService trainingService) {
        this.authService = authService;
        this.syncService = syncService;
        this.workoutService = workoutService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.trainingService = trainingService;
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody ZwiftLoginRequest request) {
        if (!authService.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Zwift integration is not enabled"));
        }

        String userId = SecurityUtils.getCurrentUserId();

        try {
            ZwiftAuthService.ZwiftTokenResponse tokenResponse =
                    authService.authenticate(request.username(), request.password());

            User user = userService.linkZwift(userId, tokenResponse.zwiftUserId(),
                    tokenResponse.accessToken(), tokenResponse.refreshToken());

            return ResponseEntity.ok(userService.userToMap(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to connect Zwift: " + e.getMessage()));
        }
    }

    @PostMapping("/import-history")
    public ZwiftActivitySyncService.SyncResult importHistory() {
        return syncService.importHistory(SecurityUtils.getCurrentUserId());
    }

    /**
     * Push a single training workout to Zwift.
     */
    @PostMapping("/push-workout/{trainingId}")
    public ResponseEntity<Map<String, Object>> pushWorkout(@PathVariable String trainingId) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Training training = trainingService.getTrainingById(trainingId);

        boolean success = workoutService.pushWorkoutToZwift(user, training);
        return success
                ? ResponseEntity.ok(Map.of("success", true))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", "Failed to push workout to Zwift"));
    }

    /**
     * Toggle auto-sync workouts to Zwift.
     */
    @PutMapping("/auto-sync")
    public ResponseEntity<Map<String, Object>> setAutoSync(@RequestBody Map<String, Boolean> body) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        user.setZwiftAutoSyncWorkouts(enabled);
        userRepository.save(user);

        return ResponseEntity.ok(userService.userToMap(user));
    }

    public record ZwiftLoginRequest(String username, String password) {}
}
