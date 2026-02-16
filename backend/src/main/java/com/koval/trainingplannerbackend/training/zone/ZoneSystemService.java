package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.WorkoutBlock;
import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ZoneSystemService {

    private final ZoneSystemRepository zoneSystemRepository;
    private final TagRepository tagRepository;
    private final com.koval.trainingplannerbackend.auth.UserRepository userRepository;

    public ZoneSystemService(ZoneSystemRepository zoneSystemRepository,
            TagRepository tagRepository,
            com.koval.trainingplannerbackend.auth.UserRepository userRepository) {
        this.zoneSystemRepository = zoneSystemRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
    }

    public ZoneSystem createZoneSystem(ZoneSystem zoneSystem) {
        zoneSystem.setCreatedAt(LocalDateTime.now());
        zoneSystem.setUpdatedAt(LocalDateTime.now());

        // If this is the first system for the coach, make it active and default
        List<ZoneSystem> existingSystems = zoneSystemRepository.findByCoachId(zoneSystem.getCoachId());
        if (existingSystems.isEmpty()) {
            zoneSystem.setActive(true);
            zoneSystem.setDefault(true);
        }

        return zoneSystemRepository.save(zoneSystem);
    }

    public ZoneSystem updateZoneSystem(String id, ZoneSystem updates) {
        ZoneSystem existing = zoneSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem not found: " + id));

        if (!existing.getCoachId().equals(updates.getCoachId())) {
            throw new IllegalArgumentException("Cannot change coachId of a ZoneSystem");
        }

        existing.setName(updates.getName());
        existing.setZones(updates.getZones());
        existing.setUpdatedAt(LocalDateTime.now());

        return zoneSystemRepository.save(existing);
    }

    public void deleteZoneSystem(String id, String coachId) {
        ZoneSystem existing = zoneSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem not found: " + id));

        if (!existing.getCoachId().equals(coachId)) {
            throw new IllegalArgumentException("ZoneSystem does not belong to this coach");
        }

        zoneSystemRepository.deleteById(id);
    }

    public List<ZoneSystem> getZoneSystemsForCoach(String coachId) {
        return zoneSystemRepository.findByCoachId(coachId);
    }

    @Transactional
    public void setActiveSystem(String coachId, String zoneSystemId) {
        List<ZoneSystem> systems = zoneSystemRepository.findByCoachId(coachId);
        boolean found = false;

        for (ZoneSystem system : systems) {
            if (system.getId().equals(zoneSystemId)) {
                system.setActive(true);
                found = true;
            } else {
                system.setActive(false);
            }
            zoneSystemRepository.save(system);
        }

        if (!found) {
            throw new IllegalArgumentException("ZoneSystem not found for coach: " + coachId);
        }
    }

    public Optional<ZoneSystem> getActiveZoneSystem(String coachId,
            com.koval.trainingplannerbackend.training.SportType sportType) {
        Optional<ZoneSystem> active = zoneSystemRepository.findByCoachIdAndSportTypeAndIsActiveTrue(coachId, sportType);
        if (active.isPresent()) {
            return active;
        }
        return zoneSystemRepository.findByCoachIdAndSportTypeAndIsDefaultTrue(coachId, sportType);
    }

    /**
     * Resolves zones for a workout block.
     */
    public WorkoutBlock resolveZoneForBlock(WorkoutBlock block, String athleteId,
            com.koval.trainingplannerbackend.training.SportType sportType) {
        if (block.zoneLabel() == null || block.zoneLabel().isEmpty()) {
            return block;
        }

        // 1. Find coaches for the athlete via Tags
        List<String> coachIds = tagRepository.findByAthleteIdsContaining(athleteId).stream()
                .map(Tag::getCoachId)
                .distinct()
                .toList();

        if (coachIds.isEmpty()) {
            return block;
        }

        // 2. Find the first coach that has an active (or default) zone system for this
        // sport
        ZoneSystem activeSystem = null;
        for (String coachId : coachIds) {
            Optional<ZoneSystem> sys = getActiveZoneSystem(coachId, sportType);
            if (sys.isPresent()) {
                activeSystem = sys.get();
                break;
            }
        }

        if (activeSystem == null) {
            return block;
        }

        // 3. Find the matching zone definition
        Optional<Zone> matchedZone = activeSystem.getZones().stream()
                .filter(z -> z.getLabel().equalsIgnoreCase(block.zoneLabel()))
                .findFirst();

        if (matchedZone.isPresent()) {
            Zone zone = matchedZone.get();
            // 4. Resolve reference value
            int referenceValue = 100;
            if (activeSystem.getReferenceType() != null) {
                Optional<com.koval.trainingplannerbackend.auth.User> userOpt = userRepository.findById(athleteId);
                if (userOpt.isPresent()) {
                    referenceValue = resolveReferenceValue(userOpt.get(), activeSystem.getReferenceType());
                }
            }

            int low = zone.getLow();
            int high = zone.getHigh();
            int avg;

            if (activeSystem.getReferenceType() != null && referenceValue > 0) {
                double base = referenceValue;
                avg = (int) Math.round(((low + high) / 2.0) * base / 100.0);
            } else {
                avg = (low + high) / 2;
            }

            Integer resolvedPower = block.powerTargetPercent();
            Integer resolvedPace = block.paceTargetSecondsPerKm();
            Integer resolvedSwimPace = block.swimPacePer100m();

            if (sportType == com.koval.trainingplannerbackend.training.SportType.RUNNING) {
                if (resolvedPace == null)
                    resolvedPace = avg;
            } else if (sportType == com.koval.trainingplannerbackend.training.SportType.SWIMMING) {
                if (resolvedSwimPace == null)
                    resolvedSwimPace = avg;
            } else {
                if (resolvedPower == null)
                    resolvedPower = avg;
            }

            return new WorkoutBlock(
                    block.type(),
                    block.durationSeconds(),
                    block.distanceMeters(),
                    block.label(),
                    block.zoneLabel(),
                    resolvedPower,
                    block.powerStartPercent(),
                    block.powerEndPercent(),
                    block.cadenceTarget(),
                    resolvedPace,
                    block.paceStartSecondsPerKm(),
                    block.paceEndSecondsPerKm(),
                    resolvedSwimPace,
                    block.swimStrokeRate());
        }
        return block;
    }

    // Helper to get effective zones for an athlete
    public List<Zone> getEffectiveZones(String athleteId,
            com.koval.trainingplannerbackend.training.SportType sportType) {
        List<String> coachIds = tagRepository.findByAthleteIdsContaining(athleteId).stream()
                .map(Tag::getCoachId)
                .distinct()
                .collect(Collectors.toList());

        if (coachIds.isEmpty()) {
            return List.of();
        }

        // Find first coach with active system for this sport
        for (String coachId : coachIds) {
            Optional<ZoneSystem> sys = getActiveZoneSystem(coachId, sportType);
            if (sys.isPresent()) {
                return sys.get().getZones();
            }
        }
        return List.of();
    }

    private int resolveReferenceValue(com.koval.trainingplannerbackend.auth.User user, ZoneReferenceType type) {
        if (type == null)
            return 100;
        switch (type) {
            case FTP:
                return user.getFtp() != null ? user.getFtp() : 100;
            case THRESHOLD_PACE:
                return user.getFunctionalThresholdPace() != null ? user.getFunctionalThresholdPace() : 0;
            case CSS:
                return user.getCriticalSwimSpeed() != null ? user.getCriticalSwimSpeed() : 0;
            case PACE_5K:
                return user.getPace5k() != null ? user.getPace5k() : 0;
            case PACE_10K:
                return user.getPace10k() != null ? user.getPace10k() : 0;
            case PACE_HALF_MARATHON:
                return user.getPaceHalfMarathon() != null ? user.getPaceHalfMarathon() : 0;
            case PACE_MARATHON:
                return user.getPaceMarathon() != null ? user.getPaceMarathon() : 0;
            default:
                return 100;
        }
    }
}
