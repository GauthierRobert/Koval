package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
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

    public ZoneSystem getZoneSystemsForCoach(String coachId, String name) {
        return zoneSystemRepository.findByCoachIdAndName(coachId, name).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ZoneSystem does not exist for this coach and name"));
    }

}
