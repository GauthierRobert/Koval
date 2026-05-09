package com.koval.trainingplannerbackend.training.group;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
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

/** REST API for coach-athlete group management (create, rename, delete, leave). */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    /** Lists groups: coach sees their own groups, athlete sees groups they belong to. */
    @GetMapping
    public ResponseEntity<List<Group>> getGroups() {
        return ResponseEntity.ok(groupService.getGroupsForUser(SecurityUtils.getCurrentUserId()));
    }

    /** Creates a new group (coach-only). */
    @PostMapping
    public ResponseEntity<Group> createGroup(@RequestBody CreateGroupRequest request) {
        Group group = groupService.createGroupAsCoach(
                SecurityUtils.getCurrentUserId(), request.name(), request.maxAthletes());
        return ResponseEntity.ok(group);
    }

    /** Renames a group (coach-only, must own the group). */
    @PutMapping("/{id}")
    public ResponseEntity<Group> renameGroup(@PathVariable String id, @RequestBody RenameGroupRequest request) {
        Group group = groupService.renameGroupAsCoach(SecurityUtils.getCurrentUserId(), id, request.name());
        return ResponseEntity.ok(group);
    }

    /** Removes the authenticated athlete from the specified group. */
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable String id) {
        groupService.removeAthleteFromGroup(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** Deletes a group (coach-only, must own the group). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        groupService.deleteGroupAsCoach(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    record CreateGroupRequest(String name, int maxAthletes) {}
    record RenameGroupRequest(String name) {}
}
