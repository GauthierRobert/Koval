package com.koval.trainingplannerbackend.integration.zwift;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Training workouts to ZWO XML and pushes them to Zwift's Training API.
 */
@Service
public class ZwiftWorkoutService {

    private static final Logger log = LoggerFactory.getLogger(ZwiftWorkoutService.class);
    private static final String BASE_URL = "https://us-or-rly101.zwift.com";

    private final ZwiftAuthService zwiftAuthService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public ZwiftWorkoutService(ZwiftAuthService zwiftAuthService, UserRepository userRepository) {
        this.zwiftAuthService = zwiftAuthService;
        this.userRepository = userRepository;
    }

    /**
     * Push a training workout to Zwift for the given user.
     * Only works for CYCLING workouts with a connected Zwift account.
     */
    public boolean pushWorkoutToZwift(User user, Training training) {
        if (user.getZwiftAccessToken() == null || user.getZwiftUserId() == null) {
            log.debug("Zwift not connected for user {}", user.getId());
            return false;
        }

        if (!"CYCLING".equals(training.getSportType())) {
            log.debug("Skipping non-cycling training {} for Zwift push", training.getId());
            return false;
        }

        String zwo = generateZwo(training);
        return uploadWorkout(user, training.getTitle(), zwo);
    }

    /**
     * Push workout and handle auto-sync preference.
     * Called after training creation/assignment if user has zwiftAutoSyncWorkouts enabled.
     */
    public void autoSyncIfEnabled(String userId, Training training) {
        if (!"CYCLING".equals(training.getSportType())) return;

        userRepository.findById(userId).ifPresent(user -> {
            if (user.isZwiftAutoSyncWorkouts()
                    && user.getZwiftAccessToken() != null
                    && user.getZwiftUserId() != null) {
                Thread.startVirtualThread(() -> {
                    try {
                        pushWorkoutToZwift(user, training);
                    } catch (Exception e) {
                        log.warn("Auto-sync to Zwift failed for training {}: {}", training.getId(), e.getMessage());
                    }
                });
            }
        });
    }

    // ── ZWO XML generation ──────────────────────────────────────────────

    public String generateZwo(Training training) {
        List<WorkoutElement> flat = flattenElements(training.getBlocks());
        StringBuilder workout = new StringBuilder();

        for (WorkoutElement block : flat) {
            String xml = blockToZwiftXml(block);
            if (!xml.isEmpty()) {
                workout.append("        ").append(xml).append("\n");
            }
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workout_file>
                    <author>Koval Training Planner</author>
                    <name>%s</name>
                    <description>%s</description>
                    <sportType>bike</sportType>
                    <tags></tags>
                    <workout>
                %s    </workout>
                </workout_file>""".formatted(
                escapeXml(training.getTitle()),
                escapeXml(training.getDescription() != null ? training.getDescription() : ""),
                workout.toString());
    }

    private String blockToZwiftXml(WorkoutElement block) {
        if (block.type() == null) return "";
        int duration = block.durationSeconds() != null ? block.durationSeconds() : 0;
        if (duration == 0 && block.type() != BlockType.PAUSE) return "";

        String label = block.label() != null ? escapeXml(block.label()) : "";

        return switch (block.type()) {
            case PAUSE -> "<SteadyState Duration=\"%d\" Power=\"0\" pace=\"0\"><textevent timeoffset=\"0\" message=\"REST: %s\"/></SteadyState>"
                    .formatted(duration, label);
            case WARMUP -> {
                double power = (block.intensityTarget() != null ? block.intensityTarget() : 50) / 100.0;
                yield "<Warmup Duration=\"%d\" PowerLow=\"0.50\" PowerHigh=\"%.2f\" pace=\"0\"><textevent timeoffset=\"10\" message=\"%s\"/></Warmup>"
                        .formatted(duration, power, label);
            }
            case COOLDOWN -> {
                double power = (block.intensityTarget() != null ? block.intensityTarget() : 50) / 100.0;
                yield "<Cooldown Duration=\"%d\" PowerLow=\"%.2f\" PowerHigh=\"0.50\" pace=\"0\"><textevent timeoffset=\"10\" message=\"%s\"/></Cooldown>"
                        .formatted(duration, power, label);
            }
            case RAMP -> {
                double low = (block.intensityStart() != null ? block.intensityStart() : 50) / 100.0;
                double high = (block.intensityEnd() != null ? block.intensityEnd() : 100) / 100.0;
                yield "<Ramp Duration=\"%d\" PowerLow=\"%.2f\" PowerHigh=\"%.2f\" pace=\"0\"><textevent timeoffset=\"10\" message=\"%s\"/></Ramp>"
                        .formatted(duration, low, high, label);
            }
            case FREE -> "<FreeRide Duration=\"%d\" Cadence=\"85\"><textevent timeoffset=\"10\" message=\"%s - Ride by feel\"/></FreeRide>"
                    .formatted(duration, label);
            case STEADY, INTERVAL -> {
                double power = (block.intensityTarget() != null ? block.intensityTarget() : 75) / 100.0;
                int cadence = block.cadenceTarget() != null ? block.cadenceTarget() : 90;
                yield "<SteadyState Duration=\"%d\" Power=\"%.2f\" pace=\"0\" Cadence=\"%d\"><textevent timeoffset=\"10\" message=\"%s\"/></SteadyState>"
                        .formatted(duration, power, cadence, label);
            }
        };
    }

    // ── Zwift API upload ────────────────────────────────────────────────

    private boolean uploadWorkout(User user, String name, String zwoXml) {
        String url = BASE_URL + "/api/profiles/" + user.getZwiftUserId() + "/workouts";
        String token = user.getZwiftAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_XML);

        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(zwoXml, headers), String.class);
            log.info("Pushed workout '{}' to Zwift for user {}", name, user.getId());
            return true;
        } catch (HttpClientErrorException.Unauthorized e) {
            // Refresh and retry
            try {
                String newToken = refreshToken(user);
                headers.setBearerAuth(newToken);
                restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(zwoXml, headers), String.class);
                log.info("Pushed workout '{}' to Zwift for user {} (after token refresh)", name, user.getId());
                return true;
            } catch (Exception retryEx) {
                log.warn("Failed to push workout to Zwift after refresh: {}", retryEx.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to push workout '{}' to Zwift: {}", name, e.getMessage());
            return false;
        }
    }

    private String refreshToken(User user) {
        ZwiftAuthService.ZwiftTokenResponse refreshed = zwiftAuthService.refreshToken(user.getZwiftRefreshToken());
        user.setZwiftAccessToken(refreshed.accessToken());
        if (refreshed.refreshToken() != null) {
            user.setZwiftRefreshToken(refreshed.refreshToken());
        }
        userRepository.save(user);
        return refreshed.accessToken();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private List<WorkoutElement> flattenElements(List<WorkoutElement> elements) {
        if (elements == null) return List.of();
        List<WorkoutElement> result = new ArrayList<>();
        for (WorkoutElement el : elements) {
            if (el.isSet()) {
                List<WorkoutElement> inner = flattenElements(el.elements());
                int reps = el.repetitions() != null ? el.repetitions() : 1;
                for (int r = 0; r < reps; r++) {
                    result.addAll(inner);
                    if (r < reps - 1 && el.restDurationSeconds() != null && el.restDurationSeconds() > 0) {
                        result.add(new WorkoutElement(null, null, null, null,
                                BlockType.PAUSE, el.restDurationSeconds(), null, "Rest", null,
                                0, null, null, null, null, null));
                    }
                }
            } else {
                result.add(el);
            }
        }
        return result;
    }

    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
