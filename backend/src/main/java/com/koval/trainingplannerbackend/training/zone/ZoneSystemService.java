package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ZoneSystemService {

    private final ZoneSystemRepository zoneSystemRepository;

    public ZoneSystemService(ZoneSystemRepository zoneSystemRepository) {
        this.zoneSystemRepository = zoneSystemRepository;
    }

    public ZoneSystem createZoneSystem(ZoneSystem zoneSystem) {
        zoneSystem.setCreatedAt(LocalDateTime.now());
        zoneSystem.setUpdatedAt(LocalDateTime.now());

        return zoneSystemRepository.save(zoneSystem);
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
        existing.setZones(new ArrayList<>(updates.getZones()));
        existing.setDefaultForSport(updates.getDefaultForSport());
        existing.setAnnotations(updates.getAnnotations());
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

    public ZoneSystem getZoneSystemsForCoach(String coachId, String name) {
        return zoneSystemRepository.findByCoachIdAndName(coachId, name).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem does not exist for this coach and name"));
    }

    public ZoneSystem getZoneSystem(String id) {
        return zoneSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem not found: " + id));
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
        return zoneSystemRepository.save(target);
    }

    public Optional<ZoneSystem> getDefaultZoneSystem(String coachId, SportType sportType) {
        return zoneSystemRepository.findByCoachIdAndSportTypeAndDefaultForSportTrue(coachId, sportType);
    }

    public List<ZoneSystem> getDefaultZoneSystems(String coachId) {
        return zoneSystemRepository.findByCoachIdAndDefaultForSportTrue(coachId);
    }
}
