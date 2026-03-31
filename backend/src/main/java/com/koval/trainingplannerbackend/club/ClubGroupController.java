package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.CreateGroupRequest;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubGroupController {

    private final ClubGroupService clubGroupService;

    public ClubGroupController(ClubGroupService clubGroupService) {
        this.clubGroupService = clubGroupService;
    }

    @PostMapping("/{id}/groups")
    public ResponseEntity<ClubGroup> createGroup(@PathVariable String id,
                                                  @RequestBody CreateGroupRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.createGroup(userId, id, req.name()));
    }

    @GetMapping("/{id}/groups")
    public ResponseEntity<List<ClubGroup>> listGroups(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.listGroups(userId, id));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubGroupService.deleteGroup(userId, id, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/groups/{groupId}/join")
    public ResponseEntity<ClubGroup> joinGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.joinGroupSelf(userId, id, groupId));
    }

    @DeleteMapping("/{id}/groups/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubGroupService.leaveGroupSelf(userId, id, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/groups/{groupId}/members/{targetUserId}")
    public ResponseEntity<ClubGroup> addMemberToGroup(@PathVariable String id,
                                                       @PathVariable String groupId,
                                                       @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.addMemberToGroup(userId, id, groupId, targetUserId));
    }

    @DeleteMapping("/{id}/groups/{groupId}/members/{targetUserId}")
    public ResponseEntity<ClubGroup> removeMemberFromGroup(@PathVariable String id,
                                                            @PathVariable String groupId,
                                                            @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.removeMemberFromGroup(userId, id, groupId, targetUserId));
    }
}
