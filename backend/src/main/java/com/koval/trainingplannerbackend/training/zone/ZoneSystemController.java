package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** REST API for training zone system management (coach CRUD, athlete/club discovery). */
@RestController
@RequestMapping("/api/zones")
public class ZoneSystemController {

    private final ZoneSystemService zoneSystemService;
    private final UserService userService;

    public ZoneSystemController(ZoneSystemService zoneSystemService, UserService userService) {
        this.zoneSystemService = zoneSystemService;
        this.userService = userService;
    }

    // --- Coach Endpoints ---

    /** Creates a new zone system (coach-only). Forces the coachId to the authenticated user. */
    @PostMapping("/coach")
    public ResponseEntity<ZoneSystem> createZoneSystem(@RequestBody ZoneSystem zoneSystem) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userService.getUserById(userId).getRole() != UserRole.COACH) {
            return ResponseEntity.status(403).build();
        }
        zoneSystem.setCoachId(userId);
        return ResponseEntity.ok(zoneSystemService.createZoneSystem(zoneSystem));
    }

    /** Updates an existing zone system owned by the authenticated coach. */
    @PutMapping("/coach/{id}")
    public ResponseEntity<ZoneSystem> updateZoneSystem(@PathVariable String id, @RequestBody ZoneSystem updates) {
        String userId = SecurityUtils.getCurrentUserId();
        updates.setCoachId(userId);
        try {
            return ResponseEntity.ok(zoneSystemService.updateZoneSystem(id, updates));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Deletes a zone system owned by the authenticated coach. */
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

    /** Lists all zone systems created by the authenticated coach. */
    @GetMapping("/coach")
    public ResponseEntity<List<ZoneSystem>> getCoachZoneSystems() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(zoneSystemService.getZoneSystemsForCoach(userId));
    }

    /** Searches for a zone system by name among those owned by the authenticated coach. */
    @GetMapping("/coach/search")
    public ResponseEntity<ZoneSystem> getCoachZoneSystemsByName(@RequestParam String name) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(zoneSystemService.getZoneSystemsForCoach(userId, name));
    }

    /** Sets or unsets a zone system as the default for its sport (un-defaults the previous one if needed). */
    @PutMapping("/coach/{id}/default")
    public ResponseEntity<ZoneSystem> setDefaultForSport(@PathVariable String id,
                                                          @RequestParam boolean value) {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            return ResponseEntity.ok(zoneSystemService.setDefaultForSport(id, userId, value));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Returns all zone systems accessible to the user: own (if coach), coach-athlete, and club coaches. */
    @GetMapping("/my-zones")
    public ResponseEntity<List<ZoneSystem>> getMyZoneSystems() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);

        List<ZoneSystem> result = new ArrayList<>();
        Set<String> existingIds = new HashSet<>();

        if (user.getRole() == UserRole.COACH) {
            addWithoutDuplicates(result, zoneSystemService.getZoneSystemsForCoach(userId), existingIds);
        }
        addWithoutDuplicates(result, zoneSystemService.getZoneSystemsForAthlete(userId), existingIds);
        addWithoutDuplicates(result, zoneSystemService.getZoneSystemsFromClubCoaches(userId), existingIds);

        return ResponseEntity.ok(result);
    }

    /** Retrieves a single zone system by ID (with access control). */
    @GetMapping("/{id}")
    public ResponseEntity<ZoneSystem> getZoneSystem(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(zoneSystemService.getZoneSystemWithAccess(id, userId));
    }

    private void addWithoutDuplicates(List<ZoneSystem> result, List<ZoneSystem> additions, Set<String> existingIds) {
        for (ZoneSystem z : additions) {
            if (existingIds.add(z.getId())) {
                result.add(z);
            }
        }
    }
}
