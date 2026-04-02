package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.group.Group;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coach")
public class CoachGroupController {

    private final CoachGroupService coachGroupService;

    public CoachGroupController(CoachGroupService coachGroupService) {
        this.coachGroupService = coachGroupService;
    }

    @PutMapping("/athletes/{athleteId}/groups")
    public ResponseEntity<List<Group>> setAthleteGroups(
            @PathVariable String athleteId,
            @RequestBody Map<String, List<String>> body) {
        String coachId = SecurityUtils.getCurrentUserId();
        List<String> groupIds = body.get("groups");
        if (groupIds == null) groupIds = List.of();
        return ResponseEntity.ok(coachGroupService.setAthleteGroups(coachId, athleteId, groupIds));
    }

    @PostMapping("/athletes/{athleteId}/groups")
    public ResponseEntity<Group> addAthleteGroup(
            @PathVariable String athleteId,
            @RequestBody Map<String, String> body) {
        String coachId = SecurityUtils.getCurrentUserId();
        String groupName = body.get("group");
        if (groupName == null || groupName.isBlank()) {
            throw new com.koval.trainingplannerbackend.config.exceptions.ValidationException("Group name is required");
        }
        return ResponseEntity.ok(coachGroupService.addGroupToAthlete(coachId, athleteId, groupName));
    }

    @DeleteMapping("/athletes/{athleteId}/groups/{groupName}")
    public ResponseEntity<Group> removeAthleteGroup(
            @PathVariable String athleteId,
            @PathVariable String groupName) {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(coachGroupService.removeGroupFromAthlete(coachId, athleteId, groupName));
    }

    @GetMapping("/athletes/groups")
    public ResponseEntity<List<Group>> getAllGroups() {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(coachGroupService.getAthleteGroupsForCoach(coachId));
    }
}
