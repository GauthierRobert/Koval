package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.group.GroupService;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ZoneSystemService {

    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private final ZoneSystemRepository zoneSystemRepository;
    private final GroupService groupService;

    private record CacheEntry(List<ZoneSystem> value, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private final Map<String, CacheEntry> defaultZoneCache = new ConcurrentHashMap<>();

    public ZoneSystemService(ZoneSystemRepository zoneSystemRepository, GroupService groupService) {
        this.zoneSystemRepository = zoneSystemRepository;
        this.groupService = groupService;
    }

    public ZoneSystem createZoneSystem(ZoneSystem zoneSystem) {
        zoneSystem.setCreatedAt(LocalDateTime.now());
        zoneSystem.setUpdatedAt(LocalDateTime.now());
        ZoneSystem saved = zoneSystemRepository.save(zoneSystem);
        defaultZoneCache.remove(zoneSystem.getCoachId());
        return saved;
    }

    public ZoneSystem updateZoneSystem(String id, ZoneSystem updates) {
        ZoneSystem existing = zoneSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem not found: " + id));

        if (!existing.getCoachId().equals(updates.getCoachId())) {
            throw new IllegalArgumentException("Cannot change coachId of a ZoneSystem");
        }

        existing.setName(updates.getName());
        existing.setSportType(updates.getSportType());
        existing.setReferenceType(updates.getReferenceType());
        existing.setReferenceName(updates.getReferenceName());
        existing.setReferenceUnit(updates.getReferenceUnit());
        existing.setZones(new ArrayList<>(updates.getZones()));
        existing.setDefaultForSport(updates.getDefaultForSport());
        existing.setAnnotations(updates.getAnnotations());
        existing.setUpdatedAt(LocalDateTime.now());
        ZoneSystem saved = zoneSystemRepository.save(existing);
        defaultZoneCache.remove(existing.getCoachId());
        return saved;
    }

    public void deleteZoneSystem(String id, String coachId) {
        ZoneSystem existing = zoneSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem not found: " + id));

        if (!existing.getCoachId().equals(coachId)) {
            throw new IllegalArgumentException("ZoneSystem does not belong to this coach");
        }

        zoneSystemRepository.deleteById(id);
        defaultZoneCache.remove(coachId);
    }

    public List<ZoneSystem> getZoneSystemsForCoach(String coachId) {
        return zoneSystemRepository.findByCoachId(coachId);
    }

    public ZoneSystem getZoneSystemsForCoach(String coachId, String name) {
        return zoneSystemRepository.findByCoachIdAndName(coachId, name).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem does not exist for this coach and name"));
    }

    public ZoneSystem getZoneSystem(String id) {
        return zoneSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem not found: " + id));
    }

    public ZoneSystem getZoneSystemWithAccess(String id, String userId) {
        ZoneSystem zs = getZoneSystem(id);
        if (userId.equals(zs.getCoachId())) return zs;
        List<String> coachIds = groupService.getCoachIdsForAthlete(userId);
        if (coachIds.contains(zs.getCoachId())) return zs;
        throw new AccessDeniedException("You do not have access to this zone system");
    }

    public ZoneSystem setDefaultForSport(String zoneSystemId, String coachId, boolean isDefault) {
        ZoneSystem target = zoneSystemRepository.findById(zoneSystemId)
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem not found: " + zoneSystemId));

        if (!target.getCoachId().equals(coachId)) {
            throw new IllegalArgumentException("ZoneSystem does not belong to this coach");
        }

        if (isDefault) {
            // Un-default the current default for the same coach+sport
            zoneSystemRepository.findByCoachIdAndSportTypeAndDefaultForSportTrue(coachId, target.getSportType())
                    .ifPresent(current -> {
                        if (!current.getId().equals(zoneSystemId)) {
                            current.setDefaultForSport(false);
                            current.setUpdatedAt(LocalDateTime.now());
                            zoneSystemRepository.save(current);
                        }
                    });
        }

        target.setDefaultForSport(isDefault);
        target.setUpdatedAt(LocalDateTime.now());
        ZoneSystem saved = zoneSystemRepository.save(target);
        defaultZoneCache.remove(coachId);
        return saved;
    }

    public Optional<ZoneSystem> getDefaultZoneSystem(String coachId, SportType sportType) {
        return zoneSystemRepository.findByCoachIdAndSportTypeAndDefaultForSportTrue(coachId, sportType);
    }

    public List<ZoneSystem> getDefaultZoneSystems(String coachId) {
        CacheEntry entry = defaultZoneCache.get(coachId);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }
        List<ZoneSystem> result = zoneSystemRepository.findByCoachIdAndDefaultForSportTrue(coachId);
        defaultZoneCache.put(coachId, new CacheEntry(result, System.currentTimeMillis()));
        return result;
    }

    public List<ZoneSystem> getZoneSystemsForAthlete(String athleteId) {
        List<String> coachIds = groupService.getCoachIdsForAthlete(athleteId);
        if (coachIds.isEmpty()) return List.of();
        return zoneSystemRepository.findByCoachIdIn(coachIds);
    }
}
