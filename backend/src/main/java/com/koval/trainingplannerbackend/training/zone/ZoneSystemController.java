package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
@CrossOrigin(origins = "*")
public class ZoneSystemController {

    private final ZoneSystemService zoneSystemService;
    private final UserService userService;

    public ZoneSystemController(ZoneSystemService zoneSystemService, UserService userService) {
        this.zoneSystemService = zoneSystemService;
        this.userService = userService;
    }

    // --- Coach Endpoints ---

    @PostMapping("/coach")
    public ResponseEntity<ZoneSystem> createZoneSystem(@RequestBody ZoneSystem zoneSystem) {
        String userId = SecurityUtils.getCurrentUserId();
        // Ensure the creating user is a coach
        if (userService.getUserById(userId).getRole() != UserRole.COACH) {
            return ResponseEntity.status(403).build();
        }

        // Force the coach ID to be the current user
        zoneSystem.setCoachId(userId);

        return ResponseEntity.ok(zoneSystemService.createZoneSystem(zoneSystem));
    }

    @PutMapping("/coach/{id}")
    public ResponseEntity<ZoneSystem> updateZoneSystem(@PathVariable String id, @RequestBody ZoneSystem updates) {
        String userId = SecurityUtils.getCurrentUserId();
        // Basic check, service handles ownership check
        updates.setCoachId(userId);

        try {
            return ResponseEntity.ok(zoneSystemService.updateZoneSystem(id, updates));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/coach/{id}")
    public ResponseEntity<Void> deleteZoneSystem(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            zoneSystemService.deleteZoneSystem(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/coach")
    public ResponseEntity<List<ZoneSystem>> getCoachZoneSystems() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(zoneSystemService.getZoneSystemsForCoach(userId));
    }

    @PutMapping("/coach/{id}/active")
    public ResponseEntity<Void> setActiveZoneSystem(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            zoneSystemService.setActiveSystem(userId, id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- Athlete Endpoints ---

    @GetMapping("/athlete/effective")
    public ResponseEntity<List<Zone>> getEffectiveZones(
            @RequestParam(required = false, defaultValue = "CYCLING") com.koval.trainingplannerbackend.training.SportType sportType) {
        String userId = SecurityUtils.getCurrentUserId();
        // Resolve zones for the current athlete (from their primary coach) for the
        // specified sport
        return ResponseEntity.ok(zoneSystemService.getEffectiveZones(userId, sportType));
    }
}
