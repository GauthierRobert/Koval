package com.koval.trainingplannerbackend.race;

import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParser;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParseResult;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class RaceService {

    private final RaceRepository repository;
    private final GpxParser gpxParser;

    public RaceService(RaceRepository repository, GpxParser gpxParser) {
        this.repository = repository;
        this.gpxParser = gpxParser;
    }

    public List<Race> searchRaces(String query, String sport, String region) {
        if (query != null && !query.isBlank() && sport != null && !sport.isBlank()) {
            return repository.findByTitleContainingIgnoreCaseAndSportIgnoreCase(query.trim(), sport.trim());
        }
        if (query != null && !query.isBlank()) {
            return repository.findByTitleContainingIgnoreCase(query.trim());
        }
        if (sport != null && !sport.isBlank()) {
            return repository.findBySportIgnoreCase(sport.trim());
        }
        if (region != null && !region.isBlank()) {
            return repository.findByCountryIgnoreCaseOrRegionIgnoreCase(region.trim(), region.trim());
        }
        return repository.findAll();
    }

    public Race getRaceById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Race not found"));
    }

    public Race createRace(String userId, Race race) {
        race.setCreatedBy(userId);
        race.setCreatedAt(LocalDateTime.now());
        return repository.save(race);
    }

    public Race updateRace(String id, Race updates) {
        Race existing = getRaceById(id);
        if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
        if (updates.getSport() != null) existing.setSport(updates.getSport());
        if (updates.getLocation() != null) existing.setLocation(updates.getLocation());
        if (updates.getCountry() != null) existing.setCountry(updates.getCountry());
        if (updates.getRegion() != null) existing.setRegion(updates.getRegion());
        if (updates.getDistance() != null) existing.setDistance(updates.getDistance());
        if (updates.getSwimDistanceM() != null) existing.setSwimDistanceM(updates.getSwimDistanceM());
        if (updates.getBikeDistanceM() != null) existing.setBikeDistanceM(updates.getBikeDistanceM());
        if (updates.getRunDistanceM() != null) existing.setRunDistanceM(updates.getRunDistanceM());
        if (updates.getElevationGainM() != null) existing.setElevationGainM(updates.getElevationGainM());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getWebsite() != null) existing.setWebsite(updates.getWebsite());
        if (updates.getTypicalMonth() != null) existing.setTypicalMonth(updates.getTypicalMonth());
        return repository.save(existing);
    }

    public void uploadGpx(String raceId, String discipline, byte[] gpxBytes) {
        Race race = getRaceById(raceId);
        switch (discipline.toLowerCase()) {
            case "swim" -> race.setSwimGpx(gpxBytes);
            case "bike" -> race.setBikeGpx(gpxBytes);
            case "run" -> race.setRunGpx(gpxBytes);
            default -> throw new IllegalArgumentException("Discipline must be swim, bike, or run");
        }
        repository.save(race);
    }

    public void deleteGpx(String raceId, String discipline) {
        Race race = getRaceById(raceId);
        switch (discipline.toLowerCase()) {
            case "swim" -> race.setSwimGpx(null);
            case "bike" -> race.setBikeGpx(null);
            case "run" -> race.setRunGpx(null);
            default -> throw new IllegalArgumentException("Discipline must be swim, bike, or run");
        }
        repository.save(race);
    }

    public byte[] getGpxBytes(String raceId, String discipline) {
        Race race = getRaceById(raceId);
        byte[] gpx = switch (discipline.toLowerCase()) {
            case "swim" -> race.getSwimGpx();
            case "bike" -> race.getBikeGpx();
            case "run" -> race.getRunGpx();
            default -> throw new IllegalArgumentException("Discipline must be swim, bike, or run");
        };
        if (gpx == null) {
            throw new NoSuchElementException("No GPX file for discipline: " + discipline);
        }
        return gpx;
    }

    public List<RouteCoordinate> getRouteCoordinates(String raceId, String discipline) {
        byte[] gpxBytes = getGpxBytes(raceId, discipline);
        double segmentLength = "run".equalsIgnoreCase(discipline) ? GpxParser.RUN_SEGMENT_LENGTH_M : 500.0;
        GpxParseResult result = gpxParser.parseWithCoordinates(new ByteArrayInputStream(gpxBytes), segmentLength);
        return result.routeCoordinates();
    }

    public GpxParseResult parseGpx(String raceId, String discipline) {
        byte[] gpxBytes = getGpxBytes(raceId, discipline);
        double segmentLength = "run".equalsIgnoreCase(discipline) ? GpxParser.RUN_SEGMENT_LENGTH_M : 500.0;
        return gpxParser.parseWithCoordinates(new ByteArrayInputStream(gpxBytes), segmentLength);
    }
}
