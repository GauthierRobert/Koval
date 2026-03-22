package com.koval.trainingplannerbackend.training.group;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@CrossOrigin(origins = "*")
public class GroupController {

    private final GroupService groupService;
    private final UserRepository userRepository;

    public GroupController(GroupService groupService, UserRepository userRepository) {
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Group>> getGroups() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }
        if (user.isCoach()) {
            return ResponseEntity.ok(groupService.getGroupsForCoach(userId));
        } else {
            return ResponseEntity.ok(groupService.getGroupsForAthlete(userId));
        }
    }

    @PostMapping
    public ResponseEntity<Group> createGroup(@RequestBody CreateGroupRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isCoach()) {
            return ResponseEntity.status(403).build();
        }
        Group group = groupService.getOrCreateGroup(request.name(), userId, request.maxAthletes());
        return ResponseEntity.ok(group);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Group> renameGroup(@PathVariable String id, @RequestBody RenameGroupRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isCoach()) {
            throw new ForbiddenOperationException("Only coaches can rename groups");
        }
        Group group = groupService.renameGroup(id, request.name(), userId);
        return ResponseEntity.ok(group);
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        groupService.removeAthleteFromGroup(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isCoach()) {
            throw new ForbiddenOperationException("Only coaches can delete groups");
        }
        groupService.deleteGroup(id, userId);
        return ResponseEntity.noContent().build();
    }

    record CreateGroupRequest(String name, int maxAthletes) {}
    record RenameGroupRequest(String name) {}
}
