package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CoachGroupService {

    private final GroupService groupService;
    private final UserRepository userRepository;

    public CoachGroupService(GroupService groupService, UserRepository userRepository) {
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    /**
     * Get all athletes for a coach (derived from groups).
     */
    public List<User> getCoachAthletes(String coachId) {
        List<String> athleteIds = groupService.getAthleteIdsForCoach(coachId);
        if (athleteIds.isEmpty())
            return List.of();
        return userRepository.findByIdIn(athleteIds);
    }

    /**
     * Get athletes filtered by group for a specific coach.
     */
    public List<User> getAthletesByGroup(String coachId, String groupId) {
        Group group = groupService.getGroupById(groupId);
        if (!coachId.equals(group.getCoachId())) {
            throw new ForbiddenOperationException("Group does not belong to this coach");
        }
        if (group.getAthleteIds().isEmpty())
            return List.of();
        return userRepository.findByIdIn(group.getAthleteIds());
    }

    /**
     * Get all groups for a coach (Group objects).
     */
    public List<Group> getAthleteGroupsForCoach(String coachId) {
        return groupService.getGroupsForCoach(coachId);
    }

    /**
     * Add a group to an athlete. Coach-only. Creates the group if it doesn't exist,
     * then adds athlete.
     */
    public Group addGroupToAthlete(String coachId, String athleteId, String groupName) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));
        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("Only coaches can assign groups to athletes");
        }

        // Verify athlete exists
        userRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException("User", athleteId));

        Group group = groupService.getOrCreateGroup(groupName, coachId, 0);
        return groupService.addAthleteToGroup(group.getId(), athleteId);
    }

    /**
     * Remove a group from an athlete by group name. Coach-only.
     */
    public Group removeGroupFromAthlete(String coachId, String athleteId, String groupName) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));
        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("Only coaches can modify athlete groups");
        }

        Group group = groupService.getGroupByNameAndCoach(groupName, coachId);
        return groupService.removeAthleteFromGroup(group.getId(), athleteId);
    }

    /**
     * Replace all groups for an athlete under this coach.
     * Removes athlete from all current coach groups, then adds to specified groups.
     */
    public List<Group> setAthleteGroups(String coachId, String athleteId, List<String> groupIds) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));
        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("Only coaches can modify athlete groups");
        }

        // Remove from all current coach groups
        groupService.removeAthleteFromAllCoachGroups(coachId, athleteId);

        // Add to each specified group
        return groupIds.stream()
                .map(groupId -> {
                    Group group = groupService.getGroupById(groupId);
                    if (!coachId.equals(group.getCoachId())) {
                        throw new ForbiddenOperationException("Group " + groupId + " does not belong to this coach");
                    }
                    return groupService.addAthleteToGroup(groupId, athleteId);
                })
                .toList();
    }

    /**
     * Remove an athlete from a coach's roster (remove from all coach groups).
     */
    public void removeAthlete(String coachId, String athleteId) {
        groupService.removeAthleteFromAllCoachGroups(coachId, athleteId);
    }
}
