package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/coach/search")
    public ResponseEntity<ZoneSystem> getCoachZoneSystemsByName(@RequestParam String name) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(zoneSystemService.getZoneSystemsForCoach(userId, name));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneSystem> getZoneSystem(@PathVariable String id) {
        // Allow any authenticated user to read a zone system if they have the ID (for
        // now)
        // In a real app, might check if the user is the coach or an athlete assigned to
        // the coach
        return ResponseEntity.ok(zoneSystemService.getZoneSystem(id));
    }

}
