package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @GetMapping("/my-zones")
    public ResponseEntity<List<ZoneSystem>> getMyZoneSystems() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);

        List<ZoneSystem> result = new ArrayList<>();

        if (user.getRole() == UserRole.COACH) {
            result.addAll(zoneSystemService.getZoneSystemsForCoach(userId));
        }

        List<ZoneSystem> coachZones = zoneSystemService.getZoneSystemsForAthlete(userId);
        Set<String> existingIds = result.stream().map(ZoneSystem::getId).collect(Collectors.toSet());
        for (ZoneSystem z : coachZones) {
            if (!existingIds.contains(z.getId())) result.add(z);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneSystem> getZoneSystem(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(zoneSystemService.getZoneSystemWithAccess(id, userId));
    }

}
