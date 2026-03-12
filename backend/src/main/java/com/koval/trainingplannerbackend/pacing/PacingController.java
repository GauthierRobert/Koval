package com.koval.trainingplannerbackend.pacing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingPlanResponse;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParseResult;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParser;
import com.koval.trainingplannerbackend.race.RaceService;
import com.koval.trainingplannerbackend.race.Race;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/pacing")
public class PacingController {

    private final GpxParser gpxParser;
    private final PacingService pacingService;
    private final UserService userService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final PacingPlanRepository pacingPlanRepository;
    private final SimulationRequestRepository simulationRequestRepository;
    private final RaceService raceService;

    public PacingController(GpxParser gpxParser, PacingService pacingService,
                            UserService userService,
                            PacingPlanRepository pacingPlanRepository,
                            SimulationRequestRepository simulationRequestRepository,
                            RaceService raceService) {
        this.gpxParser = gpxParser;
        this.pacingService = pacingService;
        this.userService = userService;
        this.pacingPlanRepository = pacingPlanRepository;
        this.simulationRequestRepository = simulationRequestRepository;
        this.raceService = raceService;
    }

    /**
     * Generate a pacing plan from GPX file(s) + athlete profile.
     * <p>
     * For BIKE or RUN: send a single "gpx" file (or "bikeGpx"/"runGpx").
     * For TRIATHLON: send "bikeGpx" + "runGpx" (separate courses).
     * SWIM requires no GPX.
     * Loop params replicate the course N times (e.g. 5x20km = 100km).
     */
    @PostMapping("/generate")
    public ResponseEntity<PacingPlanResponse> generatePacingPlan(
            @RequestParam(value = "gpx", required = false) MultipartFile gpxFile,
            @RequestParam(value = "bikeGpx", required = false) MultipartFile bikeGpxFile,
            @RequestParam(value = "runGpx", required = false) MultipartFile runGpxFile,
            @RequestParam("profile") String profileJson,
            @RequestParam(value = "discipline", defaultValue = "TRIATHLON") String discipline,
            @RequestParam(value = "bikeLoops", defaultValue = "1") int bikeLoops,
            @RequestParam(value = "runLoops", defaultValue = "1") int runLoops) throws Exception {

        if (!List.of("SWIM", "BIKE", "RUN", "TRIATHLON").contains(discipline.toUpperCase())) {
            throw new IllegalArgumentException("Discipline must be SWIM, BIKE, RUN, or TRIATHLON");
        }

        String disc = discipline.toUpperCase();
        boolean needsBike = "BIKE".equals(disc) || "TRIATHLON".equals(disc);
        boolean needsRun = "RUN".equals(disc) || "TRIATHLON".equals(disc);
        boolean needsSwim = "SWIM".equals(disc) || "TRIATHLON".equals(disc);

        // Resolve GPX files per discipline
        GpxParseResult bikeResult = null;
        GpxParseResult runResult = null;

        if ("TRIATHLON".equals(disc)) {
            // Triathlon requires separate bike + run GPX
            if (bikeGpxFile == null || bikeGpxFile.isEmpty()) {
                throw new IllegalArgumentException("Bike GPX file is required for triathlon pacing");
            }
            if (runGpxFile == null || runGpxFile.isEmpty()) {
                throw new IllegalArgumentException("Run GPX file is required for triathlon pacing");
            }
            validateGpxFile(bikeGpxFile);
            validateGpxFile(runGpxFile);
            bikeResult = applyLoops(gpxParser.parseWithCoordinates(bikeGpxFile.getInputStream()), bikeLoops);
            runResult = applyLoops(gpxParser.parseWithCoordinates(runGpxFile.getInputStream(), GpxParser.RUN_SEGMENT_LENGTH_M), runLoops);
        } else if (needsBike) {
            MultipartFile file = bikeGpxFile != null && !bikeGpxFile.isEmpty() ? bikeGpxFile : gpxFile;
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("GPX file is required for bike pacing");
            }
            validateGpxFile(file);
            bikeResult = applyLoops(gpxParser.parseWithCoordinates(file.getInputStream()), bikeLoops);
        } else if (needsRun) {
            MultipartFile file = runGpxFile != null && !runGpxFile.isEmpty() ? runGpxFile : gpxFile;
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("GPX file is required for run pacing");
            }
            validateGpxFile(file);
            runResult = applyLoops(gpxParser.parseWithCoordinates(file.getInputStream(), GpxParser.RUN_SEGMENT_LENGTH_M), runLoops);
        }
        // SWIM: no GPX needed

        // Parse athlete profile from JSON
        AthleteProfile profile = OBJECT_MAPPER.readValue(profileJson, AthleteProfile.class);

        // Merge with user defaults
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        profile = profile.withDefaults(
                user.getFtp(), user.getWeightKg(),
                user.getFunctionalThresholdPace(), user.getCriticalSwimSpeed()
        );

        // Validate required fields after merging defaults
        if (needsBike) {
            if (profile.ftp() == null || profile.ftp() <= 0) {
                throw new IllegalArgumentException("FTP is required for bike pacing");
            }
            if (profile.weightKg() == null || profile.weightKg() <= 0) {
                throw new IllegalArgumentException("Weight is required for bike pacing");
            }
        }
        if (needsRun) {
            if (profile.thresholdPaceSec() == null || profile.thresholdPaceSec() <= 0) {
                throw new IllegalArgumentException("Threshold pace is required for run pacing");
            }
        }
        if (needsSwim) {
            if (profile.swimCssSec() == null || profile.swimCssSec() <= 0) {
                throw new IllegalArgumentException("Swim CSS is required for swim pacing");
            }
        }

        // Generate plan with separate bike/run data
        PacingPlanResponse plan = pacingService.generatePlan(
                bikeResult != null ? bikeResult.segments() : null,
                runResult != null ? runResult.segments() : null,
                profile, disc,
                bikeResult != null ? bikeResult.routeCoordinates() : null,
                runResult != null ? runResult.routeCoordinates() : null);

        return ResponseEntity.ok(plan);
    }

    private void validateGpxFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (contentType != null && !contentType.contains("xml") && !contentType.contains("gpx")
                && !contentType.equals("application/octet-stream")) {
            if (filename == null || !filename.toLowerCase().endsWith(".gpx")) {
                throw new IllegalArgumentException("File must be a GPX file");
            }
        }
    }

    private GpxParseResult applyLoops(GpxParseResult result, int loops) {
        if (loops <= 1) return result;

        var baseSegments = result.segments();
        var baseCoords = result.routeCoordinates();
        if (baseSegments.isEmpty()) return result;

        double baseDistance = baseSegments.get(baseSegments.size() - 1).endDistance();

        var loopedSegments = new java.util.ArrayList<>(baseSegments);
        var loopedCoords = new java.util.ArrayList<>(baseCoords);

        for (int lap = 1; lap < loops; lap++) {
            double offset = lap * baseDistance;
            for (var seg : baseSegments) {
                loopedSegments.add(new com.koval.trainingplannerbackend.pacing.gpx.CourseSegment(
                        seg.startDistance() + offset, seg.endDistance() + offset,
                        seg.averageGradient(), seg.elevationGain(), seg.elevationLoss(),
                        seg.startElevation(), seg.endElevation()));
            }
            for (var coord : baseCoords) {
                loopedCoords.add(new com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate(
                        coord.lat(), coord.lon(), coord.elevation(), coord.distance() + offset));
            }
        }

        return new GpxParseResult(loopedSegments, loopedCoords);
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
                0.5, "MIXED", 20.0, 0.0, null, null, null, null, "ROAD_AERO"
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
                .orElseThrow(() -> new NoSuchElementException("Pacing plan not found"));
        if (!userId.equals(plan.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your pacing plan");
        }
        pacingPlanRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Simulation Requests ─────────────────────────────────────────────

    @PostMapping("/simulation-requests")
    public ResponseEntity<SimulationRequest> saveSimulationRequest(@RequestBody SimulationRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        request.setUserId(userId);
        request.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(simulationRequestRepository.save(request));
    }

    @GetMapping("/simulation-requests")
    public ResponseEntity<List<SimulationRequest>> listSimulationRequests(
            @RequestParam(value = "goalId", required = false) String goalId) {
        String userId = SecurityUtils.getCurrentUserId();
        if (goalId != null && !goalId.isBlank()) {
            return ResponseEntity.ok(simulationRequestRepository.findByGoalIdOrderByCreatedAtDesc(goalId));
        }
        return ResponseEntity.ok(simulationRequestRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @DeleteMapping("/simulation-requests/{id}")
    public ResponseEntity<Void> deleteSimulationRequest(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        SimulationRequest req = simulationRequestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Simulation request not found"));
        if (!userId.equals(req.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your simulation request");
        }
        simulationRequestRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Generate from Race ──────────────────────────────────────────────

    @PostMapping("/generate-from-race")
    public ResponseEntity<PacingPlanResponse> generateFromRace(@RequestBody GenerateFromRaceRequest request) {
        String disc = request.discipline().toUpperCase();
        if (!List.of("SWIM", "BIKE", "RUN", "TRIATHLON").contains(disc)) {
            throw new IllegalArgumentException("Discipline must be SWIM, BIKE, RUN, or TRIATHLON");
        }

        boolean needsBike = "BIKE".equals(disc) || "TRIATHLON".equals(disc);
        boolean needsRun = "RUN".equals(disc) || "TRIATHLON".equals(disc);
        boolean needsSwim = "SWIM".equals(disc) || "TRIATHLON".equals(disc);

        Race race = raceService.getRaceById(request.raceId());

        GpxParseResult bikeResult = null;
        GpxParseResult runResult = null;

        if (needsBike && race.getBikeGpx() != null) {
            bikeResult = applyLoops(raceService.parseGpx(request.raceId(), "bike"), request.bikeLoops());
        }
        if (needsRun && race.getRunGpx() != null) {
            runResult = applyLoops(raceService.parseGpx(request.raceId(), "run"), request.runLoops());
        }

        AthleteProfile profile = request.profile();
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        profile = profile.withDefaults(
                user.getFtp(), user.getWeightKg(),
                user.getFunctionalThresholdPace(), user.getCriticalSwimSpeed()
        );

        if (needsBike) {
            if (profile.ftp() == null || profile.ftp() <= 0)
                throw new IllegalArgumentException("FTP is required for bike pacing");
            if (profile.weightKg() == null || profile.weightKg() <= 0)
                throw new IllegalArgumentException("Weight is required for bike pacing");
        }
        if (needsRun && profile.thresholdPaceSec() == null)
            throw new IllegalArgumentException("Threshold pace is required for run pacing");
        if (needsSwim && profile.swimCssSec() == null)
            throw new IllegalArgumentException("Swim CSS is required for swim pacing");

        PacingPlanResponse plan = pacingService.generatePlan(
                bikeResult != null ? bikeResult.segments() : null,
                runResult != null ? runResult.segments() : null,
                profile, disc,
                bikeResult != null ? bikeResult.routeCoordinates() : null,
                runResult != null ? runResult.routeCoordinates() : null);

        return ResponseEntity.ok(plan);
    }

    public record GenerateFromRaceRequest(
            String raceId,
            AthleteProfile profile,
            String discipline,
            int bikeLoops,
            int runLoops
    ) {
        public GenerateFromRaceRequest {
            if (bikeLoops <= 0) bikeLoops = 1;
            if (runLoops <= 0) runLoops = 1;
        }
    }
}
