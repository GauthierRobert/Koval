package com.koval.trainingplannerbackend.race;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/races")
@CrossOrigin(origins = "*")
public class RaceController {

    private final RaceService raceService;
    private final RaceCompletionService completionService;

    public RaceController(RaceService raceService, RaceCompletionService completionService) {
        this.raceService = raceService;
        this.completionService = completionService;
    }

    @GetMapping
    public ResponseEntity<List<RaceSummary>> searchRaces(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "sport", required = false) String sport,
            @RequestParam(value = "region", required = false) String region) {
        List<RaceSummary> summaries = raceService.searchRaces(query, sport, region)
                .stream()
                .map(RaceSummary::from)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RaceSummary> getRace(@PathVariable String id) {
        try {
            return ResponseEntity.ok(RaceSummary.from(raceService.getRaceById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<RaceSummary> createRace(@Valid @RequestBody Race race) {
        String userId = SecurityUtils.getCurrentUserId();
        Race created = raceService.createRace(userId, race);
        return ResponseEntity.status(HttpStatus.CREATED).body(RaceSummary.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RaceSummary> updateRace(@PathVariable String id, @RequestBody Race updates) {
        try {
            return ResponseEntity.ok(RaceSummary.from(raceService.updateRace(id, updates)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/gpx/{discipline}")
    public ResponseEntity<Void> uploadGpx(@PathVariable String id,
                                           @PathVariable String discipline,
                                           @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            raceService.uploadGpx(id, discipline, file.getBytes());
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}/gpx/{discipline}")
    public ResponseEntity<Void> deleteGpx(@PathVariable String id, @PathVariable String discipline) {
        try {
            raceService.deleteGpx(id, discipline);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/gpx/{discipline}")
    public ResponseEntity<byte[]> downloadGpx(@PathVariable String id, @PathVariable String discipline) {
        try {
            byte[] gpx = raceService.getGpxBytes(id, discipline);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + discipline + ".gpx\"")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(gpx);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/route/{discipline}")
    public ResponseEntity<List<RouteCoordinate>> getRouteCoordinates(@PathVariable String id,
                                                                       @PathVariable String discipline) {
        try {
            return ResponseEntity.ok(raceService.getRouteCoordinates(id, discipline));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/ai-complete")
    public ResponseEntity<RaceSummary> aiComplete(@PathVariable String id) {
        try {
            Race completed = completionService.completeRaceDetails(id);
            return ResponseEntity.ok(RaceSummary.from(completed));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Summary DTO that excludes raw GPX bytes from responses.
     */
    public record RaceSummary(
            String id, String title, String sport,
            String location, String country, String region, String distance,
            Double swimDistanceM, Double bikeDistanceM, Double runDistanceM,
            Integer elevationGainM, String description, String website,
            Integer typicalMonth,
            boolean hasSwimGpx, boolean hasBikeGpx, boolean hasRunGpx,
            String createdBy, boolean verified
    ) {
        static RaceSummary from(Race r) {
            return new RaceSummary(
                    r.getId(), r.getTitle(), r.getSport(),
                    r.getLocation(), r.getCountry(), r.getRegion(), r.getDistance(),
                    r.getSwimDistanceM(), r.getBikeDistanceM(), r.getRunDistanceM(),
                    r.getElevationGainM(), r.getDescription(), r.getWebsite(),
                    r.getTypicalMonth(),
                    r.getSwimGpx() != null, r.getBikeGpx() != null, r.getRunGpx() != null,
                    r.getCreatedBy(), r.isVerified()
            );
        }
    }
}
