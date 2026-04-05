package com.koval.trainingplannerbackend.pacing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingPlanResponse;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParseResult;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParser;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
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
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/pacing")
public class PacingController {

    private static final double BIKE_SEGMENT_LENGTH_M = 200.0;
    private static final double RUN_SEGMENT_LENGTH_M = 50.0;

    enum Discipline {
        SWIM(false, false, true),
        BIKE(true, false, false),
        RUN(false, true, false),
        TRIATHLON(true, true, true);

        final boolean needsBike, needsRun, needsSwim;

        Discipline(boolean needsBike, boolean needsRun, boolean needsSwim) {
            this.needsBike = needsBike;
            this.needsRun = needsRun;
            this.needsSwim = needsSwim;
        }

        static Discipline parse(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Discipline must be SWIM, BIKE, RUN, or TRIATHLON");
            }
        }
    }

    private final GpxParser gpxParser;
    private final PacingService pacingService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final SimulationRequestRepository simulationRequestRepository;
    private final RaceService raceService;

    public PacingController(GpxParser gpxParser, PacingService pacingService,
                            UserService userService, ObjectMapper objectMapper,
                            SimulationRequestRepository simulationRequestRepository,
                            RaceService raceService) {
        this.gpxParser = gpxParser;
        this.pacingService = pacingService;
        this.userService = userService;
        this.objectMapper = objectMapper;
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

        Discipline disc = Discipline.parse(discipline);

        // Resolve GPX files per discipline
        GpxParseResult bikeResult = null;
        GpxParseResult runResult = null;

        if (disc == Discipline.TRIATHLON) {
            if (bikeGpxFile == null || bikeGpxFile.isEmpty()) {
                throw new IllegalArgumentException("Bike GPX file is required for triathlon pacing");
            }
            if (runGpxFile == null || runGpxFile.isEmpty()) {
                throw new IllegalArgumentException("Run GPX file is required for triathlon pacing");
            }
            validateGpxFile(bikeGpxFile);
            validateGpxFile(runGpxFile);
            bikeResult = applyLoops(gpxParser.parseWithCoordinates(bikeGpxFile.getInputStream()), bikeLoops);
            runResult = applyLoops(gpxParser.parseWithCoordinates(runGpxFile.getInputStream()), runLoops);
        } else if (disc.needsBike) {
            MultipartFile file = bikeGpxFile != null && !bikeGpxFile.isEmpty() ? bikeGpxFile : gpxFile;
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("GPX file is required for bike pacing");
            }
            validateGpxFile(file);
            bikeResult = applyLoops(gpxParser.parseWithCoordinates(file.getInputStream()), bikeLoops);
        } else if (disc.needsRun) {
            MultipartFile file = runGpxFile != null && !runGpxFile.isEmpty() ? runGpxFile : gpxFile;
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("GPX file is required for run pacing");
            }
            validateGpxFile(file);
            runResult = applyLoops(gpxParser.parseWithCoordinates(file.getInputStream()), runLoops);
        }

        bikeResult = resampleIfPresent(bikeResult, BIKE_SEGMENT_LENGTH_M);
        runResult = resampleIfPresent(runResult, RUN_SEGMENT_LENGTH_M);

        AthleteProfile profile = objectMapper.readValue(profileJson, AthleteProfile.class);
        profile = mergeProfileAndValidate(profile, disc);

        PacingPlanResponse plan = pacingService.generatePlan(
                bikeResult != null ? bikeResult.segments() : null,
                runResult != null ? runResult.segments() : null,
                profile, disc.name(),
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

    private GpxParseResult resampleIfPresent(GpxParseResult result, double segmentLength) {
        if (result == null) return null;
        return new GpxParseResult(
                gpxParser.resampleToFixedDistance(result.segments(), segmentLength),
                result.routeCoordinates());
    }

    private AthleteProfile mergeProfileAndValidate(AthleteProfile profile, Discipline disc) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        profile = profile.withDefaults(
                user.getFtp(), user.getWeightKg(),
                user.getFunctionalThresholdPace(), user.getCriticalSwimSpeed()
        );

        if (disc.needsBike) {
            if (profile.ftp() == null || profile.ftp() <= 0)
                throw new IllegalArgumentException("FTP is required for bike pacing");
            if (profile.weightKg() == null || profile.weightKg() <= 0)
                throw new IllegalArgumentException("Weight is required for bike pacing");
        }
        if (disc.needsRun && (profile.thresholdPaceSec() == null || profile.thresholdPaceSec() <= 0))
            throw new IllegalArgumentException("Threshold pace is required for run pacing");
        if (disc.needsSwim && (profile.swimCssSec() == null || profile.swimCssSec() <= 0))
            throw new IllegalArgumentException("Swim CSS is required for swim pacing");

        return profile;
    }

    private GpxParseResult applyLoops(GpxParseResult result, int loops) {
        if (loops <= 1) return result;

        var baseSegments = result.segments();
        var baseCoords = result.routeCoordinates();
        if (baseSegments.isEmpty()) return result;

        double baseDistance = baseSegments.getLast().endDistance();

        List<com.koval.trainingplannerbackend.pacing.gpx.CourseSegment> loopedSegments = IntStream.range(0, loops)
                .mapToObj(lap -> {
                    double offset = lap * baseDistance;
                    return baseSegments.stream().map(seg -> new com.koval.trainingplannerbackend.pacing.gpx.CourseSegment(
                            seg.startDistance() + offset, seg.endDistance() + offset,
                            seg.averageGradient(), seg.elevationGain(), seg.elevationLoss(),
                            seg.startElevation(), seg.endElevation()));
                })
                .flatMap(s -> s)
                .toList();

        List<com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate> loopedCoords = IntStream.range(0, loops)
                .mapToObj(lap -> {
                    double offset = lap * baseDistance;
                    return baseCoords.stream().map(coord -> new com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate(
                            coord.lat(), coord.lon(), coord.elevation(), coord.distance() + offset));
                })
                .flatMap(s -> s)
                .toList();

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
                0.5, "MIXED", null, null, null, null, "ROAD_AERO"
        );

        return ResponseEntity.ok(defaults);
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
        Discipline disc = Discipline.parse(request.discipline());
        Race race = raceService.getRaceById(request.raceId());

        GpxParseResult bikeResult = null;
        GpxParseResult runResult = null;

        if (disc.needsBike && race.getBikeGpx() != null) {
            bikeResult = applyLoops(raceService.parseGpx(request.raceId(), "bike"), request.bikeLoops());
        }
        if (disc.needsRun && race.getRunGpx() != null) {
            runResult = applyLoops(raceService.parseGpx(request.raceId(), "run"), request.runLoops());
        }

        bikeResult = resampleIfPresent(bikeResult, BIKE_SEGMENT_LENGTH_M);
        runResult = resampleIfPresent(runResult, RUN_SEGMENT_LENGTH_M);

        AthleteProfile profile = mergeProfileAndValidate(request.profile(), disc);

        PacingPlanResponse plan = pacingService.generatePlan(
                bikeResult != null ? bikeResult.segments() : null,
                runResult != null ? runResult.segments() : null,
                profile, disc.name(),
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
