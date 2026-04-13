package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Automatically generates standard training zone systems when an athlete sets or updates
 * threshold values (FTP, threshold pace, CSS). System-generated zones are marked so they
 * can be updated without overwriting coach-created custom zones.
 */
@Service
public class ZoneAutoGenerationService {

    private final ZoneSystemRepository zoneSystemRepository;

    public ZoneAutoGenerationService(ZoneSystemRepository zoneSystemRepository) {
        this.zoneSystemRepository = zoneSystemRepository;
    }

    /**
     * Generate or update system zones for all sports where the user has threshold values.
     */
    public void generateZonesForUser(User user) {
        if (user.getFtp() != null && user.getFtp() > 0) {
            upsertSystemZone(user.getId(), SportType.CYCLING, ZoneReferenceType.FTP,
                    "Coggan Power Zones", "W", buildCyclingZones());
        }
        if (user.getFunctionalThresholdPace() != null && user.getFunctionalThresholdPace() > 0) {
            upsertSystemZone(user.getId(), SportType.RUNNING, ZoneReferenceType.THRESHOLD_PACE,
                    "Running Pace Zones", "sec/km", buildRunningZones());
        }
        if (user.getCriticalSwimSpeed() != null && user.getCriticalSwimSpeed() > 0) {
            upsertSystemZone(user.getId(), SportType.SWIMMING, ZoneReferenceType.CSS,
                    "Swimming CSS Zones", "sec/100m", buildSwimmingZones());
        }
    }

    private List<Zone> buildCyclingZones() {
        return List.of(
                new Zone("Z1 - Active Recovery", 0, 55, "Active Recovery"),
                new Zone("Z2 - Endurance", 56, 75, "Endurance"),
                new Zone("Z3 - Tempo", 76, 90, "Tempo"),
                new Zone("Z4 - Threshold", 91, 105, "Threshold"),
                new Zone("Z5 - VO2max", 106, 120, "VO2max"),
                new Zone("Z6 - Anaerobic", 121, 150, "Anaerobic Capacity"),
                new Zone("Z7 - Neuromuscular", 151, 300, "Neuromuscular Power"));
    }

    private List<Zone> buildRunningZones() {
        return List.of(
                new Zone("Z1 - Easy", 0, 80, "Easy / Recovery"),
                new Zone("Z2 - Aerobic", 81, 90, "Aerobic Endurance"),
                new Zone("Z3 - Tempo", 91, 100, "Tempo / Threshold"),
                new Zone("Z4 - VO2max", 101, 110, "VO2max Intervals"),
                new Zone("Z5 - Speed", 111, 130, "Speed / Anaerobic"));
    }

    private List<Zone> buildSwimmingZones() {
        return List.of(
                new Zone("Z1 - Recovery", 0, 80, "Recovery"),
                new Zone("Z2 - Endurance", 81, 90, "Aerobic Endurance"),
                new Zone("Z3 - Tempo", 91, 100, "Threshold"),
                new Zone("Z4 - VO2max", 101, 110, "VO2max"),
                new Zone("Z5 - Speed", 111, 130, "Sprint / Anaerobic"));
    }

    private void upsertSystemZone(String userId, SportType sport, ZoneReferenceType refType,
                                  String name, String unit, List<Zone> zones) {
        Optional<ZoneSystem> existing = zoneSystemRepository
                .findByCoachIdAndSportTypeAndSystemGeneratedTrue(userId, sport);

        if (existing.isPresent()) {
            ZoneSystem zs = existing.get();
            zs.setZones(zones);
            zs.setUpdatedAt(LocalDateTime.now());
            zoneSystemRepository.save(zs);
        } else {
            ZoneSystem zs = new ZoneSystem();
            zs.setCoachId(userId);
            zs.setName(name);
            zs.setSportType(sport);
            zs.setReferenceType(refType);
            zs.setReferenceName(refType.name());
            zs.setReferenceUnit(unit);
            zs.setZones(zones);
            zs.setSystemGenerated(true);
            zs.setDefaultForSport(true);
            zoneSystemRepository.save(zs);
        }
    }
}
