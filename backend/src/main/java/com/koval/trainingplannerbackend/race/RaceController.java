package com.koval.trainingplannerbackend.race;

import com.koval.trainingplannerbackend.ai.tools.race.RaceSummary;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/races")
public class RaceController {

    private final RaceService raceService;
    private final RaceCompletionService completionService;
    private final WebSearchRaceService webSearchService;

    public RaceController(RaceService raceService, RaceCompletionService completionService, WebSearchRaceService webSearchService) {
        this.raceService = raceService;
        this.completionService = completionService;
        this.webSearchService = webSearchService;
    }

    @GetMapping
    public ResponseEntity<Page<RaceSummary>> searchRaces(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "sport", required = false) String sport,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<RaceSummary> summaries = raceService.searchRaces(query, sport, region, PageRequest.of(page, safeSize))
                .map(RaceSummary::from);
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
            String userId = SecurityUtils.getCurrentUserId();
            Race existing = raceService.getRaceById(id);
            if (!userId.equals(existing.getCreatedBy())) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(RaceSummary.from(raceService.updateRace(id, updates)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/gpx/{discipline}")
    public ResponseEntity<Void> uploadGpx(@PathVariable String id,
                                           @PathVariable String discipline,
                                           @RequestParam("file") MultipartFile file) throws java.io.IOException {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            raceService.uploadGpx(id, discipline, file.getBytes());
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
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
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(60)))
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
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(60)))
                    .body(raceService.getRouteCoordinates(id, discipline));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/facets/sports")
    public ResponseEntity<List<SportFacet>> getSportFacets() {
        return ResponseEntity.ok(raceService.getSportFacets());
    }

    @GetMapping("/facets/countries")
    public ResponseEntity<List<CountryFacet>> getCountryFacets(
            @RequestParam("sport") String sport) {
        return ResponseEntity.ok(raceService.getCountryFacets(sport));
    }

    @GetMapping("/browse")
    public ResponseEntity<Page<RaceSummary>> browseRaces(
            @RequestParam("sport") String sport,
            @RequestParam("country") String country,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<RaceSummary> summaries = raceService.browse(sport, country, PageRequest.of(page, size))
                .map(RaceSummary::from);
        return ResponseEntity.ok(summaries);
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

    @PostMapping("/web-search")
    public ResponseEntity<RaceSummary> webSearch(@RequestBody WebSearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Race result = webSearchService.searchRaceDetails(request.query().trim());
        return ResponseEntity.ok(RaceSummary.from(result));
    }

    public record WebSearchRequest(String query) {}
    public record SportFacet(String sport, long raceCount, int countryCount) {}
    public record CountryFacet(String country, long raceCount) {}

}
