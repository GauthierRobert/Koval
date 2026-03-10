package com.koval.trainingplannerbackend.pacing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingPlanResponse;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/pacing")
public class PacingController {

    private final GpxParser gpxParser;
    private final PacingService pacingService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final PacingPlanRepository pacingPlanRepository;

    public PacingController(GpxParser gpxParser, PacingService pacingService,
                            UserService userService, ObjectMapper objectMapper,
                            PacingPlanRepository pacingPlanRepository) {
        this.gpxParser = gpxParser;
        this.pacingService = pacingService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.pacingPlanRepository = pacingPlanRepository;
    }

    /**
     * Generate a pacing plan from GPX file + athlete profile.
     * Multipart request: "gpx" file + "profile" JSON string + "discipline" string.
     */
    @PostMapping("/generate")
    public ResponseEntity<PacingPlanResponse> generatePacingPlan(
            @RequestParam("gpx") MultipartFile gpxFile,
            @RequestParam("profile") String profileJson,
            @RequestParam(value = "discipline", defaultValue = "BOTH") String discipline) throws Exception {

        if (gpxFile.isEmpty()) {
            throw new IllegalArgumentException("GPX file is required");
        }

        String contentType = gpxFile.getContentType();
        String filename = gpxFile.getOriginalFilename();
        if (contentType != null && !contentType.contains("xml") && !contentType.contains("gpx")
                && !contentType.equals("application/octet-stream")) {
            if (filename == null || !filename.toLowerCase().endsWith(".gpx")) {
                throw new IllegalArgumentException("File must be a GPX file");
            }
        }

        if (!List.of("BIKE", "RUN", "BOTH").contains(discipline.toUpperCase())) {
            throw new IllegalArgumentException("Discipline must be BIKE, RUN, or BOTH");
        }

        // Parse athlete profile from JSON
        AthleteProfile profile = objectMapper.readValue(profileJson, AthleteProfile.class);

        // Merge with user defaults
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        profile = profile.withDefaults(
                user.getFtp(), user.getWeightKg(),
                user.getFunctionalThresholdPace(), user.getCriticalSwimSpeed()
        );

        // Validate required fields after merging defaults
        if (profile.ftp() == null || profile.ftp() <= 0) {
            throw new IllegalArgumentException("FTP is required (set it in your profile or provide it in the request)");
        }
        if (profile.weightKg() == null || profile.weightKg() <= 0) {
            throw new IllegalArgumentException("Weight is required (set it in your profile or provide it in the request)");
        }
        if (("RUN".equals(discipline.toUpperCase()) || "BOTH".equals(discipline.toUpperCase()))
                && (profile.thresholdPaceSec() == null || profile.thresholdPaceSec() <= 0)) {
            throw new IllegalArgumentException("Threshold pace is required for run pacing");
        }

        // Parse GPX and generate plan
        List<CourseSegment> segments = gpxParser.parse(gpxFile.getInputStream());
        PacingPlanResponse plan = pacingService.generatePlan(segments, profile, discipline.toUpperCase());

        return ResponseEntity.ok(plan);
    }

    /**
     * Return default athlete profile from user settings.
     */
    @GetMapping("/defaults")
    public ResponseEntity<AthleteProfile> getDefaults() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);

        AthleteProfile defaults = new AthleteProfile(
                user.getFtp(), user.getWeightKg(),
                user.getFunctionalThresholdPace(), user.getCriticalSwimSpeed(),
                0.5, "MIXED", 20.0, 0.0
        );

        return ResponseEntity.ok(defaults);
    }

    /**
     * Save a generated pacing plan for later reference.
     */
    @PostMapping("/save")
    public ResponseEntity<PacingPlan> savePlan(@RequestBody PacingPlan plan) {
        String userId = SecurityUtils.getCurrentUserId();
        plan.setUserId(userId);
        return ResponseEntity.ok(pacingPlanRepository.save(plan));
    }

    /**
     * List saved pacing plans for the current user.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PacingPlan>> listPlans() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(pacingPlanRepository.findByUserId(userId));
    }

    /**
     * Delete a saved pacing plan.
     */
    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        PacingPlan plan = pacingPlanRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Pacing plan not found"));
        if (!userId.equals(plan.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your pacing plan");
        }
        pacingPlanRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
