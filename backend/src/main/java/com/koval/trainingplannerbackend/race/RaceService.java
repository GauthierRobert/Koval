package com.koval.trainingplannerbackend.race;

import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParseResult;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParser;
import org.bson.Document;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;


@Service
public class RaceService {

    private final RaceRepository repository;
    private final GpxParser gpxParser;
    private final MongoTemplate mongoTemplate;

    public RaceService(RaceRepository repository, GpxParser gpxParser, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.gpxParser = gpxParser;
        this.mongoTemplate = mongoTemplate;
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

    @Cacheable(value = "races", key = "#id")
    public Race getRaceById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Race not found"));
    }

    public Race createRace(String userId, Race race) {
        race.setCreatedBy(userId);
        race.setCreatedAt(LocalDateTime.now());
        return repository.save(race);
    }

    @Caching(evict = {
            @CacheEvict(value = "races", key = "#raceId"),
            @CacheEvict(value = "raceSportFacets", allEntries = true),
            @CacheEvict(value = "raceCountryFacets", allEntries = true)
    })
    public Race updateRace(String id, Race updates) {
        validateLoops(updates.getSwimGpxLoops());
        validateLoops(updates.getBikeGpxLoops());
        validateLoops(updates.getRunGpxLoops());
        Race existing = getRaceById(id);
        mergeIfPresent(updates.getTitle(), existing::setTitle);
        mergeIfPresent(updates.getSport(), existing::setSport);
        mergeIfPresent(updates.getLocation(), existing::setLocation);
        mergeIfPresent(updates.getCountry(), existing::setCountry);
        mergeIfPresent(updates.getRegion(), existing::setRegion);
        mergeIfPresent(updates.getDistance(), existing::setDistance);
        mergeIfPresent(updates.getSwimDistanceM(), existing::setSwimDistanceM);
        mergeIfPresent(updates.getBikeDistanceM(), existing::setBikeDistanceM);
        mergeIfPresent(updates.getRunDistanceM(), existing::setRunDistanceM);
        mergeIfPresent(updates.getElevationGainM(), existing::setElevationGainM);
        mergeIfPresent(updates.getDescription(), existing::setDescription);
        mergeIfPresent(updates.getWebsite(), existing::setWebsite);
        mergeIfPresent(updates.getScheduledDate(), existing::setScheduledDate);
        mergeIfPresent(updates.getSwimGpxLoops(), existing::setSwimGpxLoops);
        mergeIfPresent(updates.getBikeGpxLoops(), existing::setBikeGpxLoops);
        mergeIfPresent(updates.getRunGpxLoops(), existing::setRunGpxLoops);
        return repository.save(existing);
    }

    private <T> void mergeIfPresent(T value, java.util.function.Consumer<T> setter) {
        if (value != null) setter.accept(value);
    }

    private void validateLoops(Integer loops) {
        if (loops != null && loops < 1) {
            throw new IllegalArgumentException("Loop count must be at least 1");
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "races", key = "#raceId"),
        @CacheEvict(value = "raceRoutes", allEntries = true),
        @CacheEvict(value = "raceSportFacets", allEntries = true),
        @CacheEvict(value = "raceCountryFacets", allEntries = true)
    })
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

    @Caching(evict = {
        @CacheEvict(value = "races", key = "#raceId"),
        @CacheEvict(value = "raceRoutes", allEntries = true)
    })
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

    @Cacheable(value = "raceRoutes", key = "#raceId + '_' + #discipline")
    public List<RouteCoordinate> getRouteCoordinates(String raceId, String discipline) {
        byte[] gpxBytes = getGpxBytes(raceId, discipline);
        GpxParseResult result = gpxParser.parseWithCoordinates(new ByteArrayInputStream(gpxBytes));
        return result.routeCoordinates();
    }

    public GpxParseResult parseGpx(String raceId, String discipline) {
        byte[] gpxBytes = getGpxBytes(raceId, discipline);
        return gpxParser.parseWithCoordinates(new ByteArrayInputStream(gpxBytes));
    }

    @Cacheable("raceSportFacets")
    public List<RaceController.SportFacet> getSportFacets() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.group("sport")
                        .count().as("raceCount")
                        .addToSet("country").as("countries"),
                Aggregation.project("raceCount")
                        .and("_id").as("sport")
                        .and("countries").size().as("countryCount"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "raceCount")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "races", Document.class);
        return results.getMappedResults().stream()
                .map(doc -> new RaceController.SportFacet(
                        doc.getString("sport"),
                        doc.getInteger("raceCount", 0),
                        doc.getInteger("countryCount", 0)
                ))
                .toList();
    }

    @Cacheable(value = "raceCountryFacets", key = "#sport")
    public List<RaceController.CountryFacet> getCountryFacets(String sport) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("sport").regex("^" + sport + "$", "i")),
                Aggregation.group("country")
                        .count().as("raceCount"),
                Aggregation.project("raceCount")
                        .and("_id").as("country"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "raceCount")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "races", Document.class);
        return results.getMappedResults().stream()
                .map(doc -> new RaceController.CountryFacet(
                        doc.getString("country"),
                        doc.getInteger("raceCount", 0)
                ))
                .toList();
    }

    public Page<Race> browse(String sport, String country, Pageable pageable) {
        return repository.findBySportIgnoreCaseAndCountryIgnoreCase(sport, country, pageable);
    }
}
