package com.koval.trainingplannerbackend.training.group;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    /**
     * Get or create a group for a specific coach.
     */
    public Group getOrCreateGroup(String name, String coachId, int maxAthletes) {
        String normalized = name.toLowerCase().trim();
        return groupRepository.findByCoachIdAndName(coachId, normalized).orElseGet(() -> {
            Group group = new Group();
            group.setName(normalized);
            group.setCoachId(coachId);
            group.setMaxAthletes(maxAthletes);
            group.setCreatedAt(LocalDateTime.now());
            return groupRepository.save(group);
        });
    }

    /**
     * Get all groups for a specific coach.
     */
    public List<Group> getGroupsForCoach(String coachId) {
        return groupRepository.findByCoachId(coachId);
    }

    /**
     * Get all groups that contain this athlete.
     */
    public List<Group> getGroupsForAthlete(String athleteId) {
        return groupRepository.findByAthleteIdsContaining(athleteId);
    }

    /**
     * Add an athlete to a group.
     */
    public Group addAthleteToGroup(String groupId, String athleteId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
        if (!group.getAthleteIds().contains(athleteId)) {
            group.getAthleteIds().add(athleteId);
            groupRepository.save(group);
        }
        return group;
    }

    /**
     * Remove an athlete from a group.
     */
    public Group removeAthleteFromGroup(String groupId, String athleteId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
        group.getAthleteIds().remove(athleteId);
        return groupRepository.save(group);
    }

    /**
     * Remove an athlete from all groups owned by a specific coach.
     */
    @Transactional
    public void removeAthleteFromAllCoachGroups(String coachId, String athleteId) {
        List<Group> modified = groupRepository.findByCoachId(coachId).stream()
                .filter(g -> g.getAthleteIds().remove(athleteId))
                .toList();
        if (!modified.isEmpty()) {
            groupRepository.saveAll(modified);
        }
    }

    /**
     * Get all unique athlete IDs across all groups for a coach.
     */
    public List<String> getAthleteIdsForCoach(String coachId) {
        return groupRepository.findByCoachId(coachId).stream()
                .flatMap(g -> g.getAthleteIds().stream())
                .distinct()
                .toList();
    }

    /**
     * Check if an athlete has any coach (is in any group).
     */
    public boolean athleteHasCoach(String athleteId) {
        return groupRepository.existsByAthleteIdsContaining(athleteId);
    }

    /**
     * Get all unique coach IDs for an athlete.
     */
    public List<String> getCoachIdsForAthlete(String athleteId) {
        return groupRepository.findByAthleteIdsContaining(athleteId).stream()
                .map(Group::getCoachId)
                .distinct()
                .toList();
    }

    /**
     * Rename a group. Only the coach who owns it can rename.
     */
    public Group renameGroup(String groupId, String newName, String coachId) {
        Group group = getGroupOwnedBy(groupId, coachId);
        group.setName(newName.toLowerCase().trim());
        return groupRepository.save(group);
    }

    /**
     * Delete a group. Only the coach who owns it can delete.
     */
    public void deleteGroup(String groupId, String coachId) {
        getGroupOwnedBy(groupId, coachId);
        groupRepository.deleteById(groupId);
    }

    private Group getGroupOwnedBy(String groupId, String coachId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
        if (!coachId.equals(group.getCoachId())) {
            throw new ForbiddenOperationException("Only the group owner can perform this operation");
        }
        return group;
    }

    /**
     * Get a group by ID.
     */
    public Group getGroupById(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
    }

    /**
     * Get a group by name (case-insensitive) for a specific coach.
     */
    public Group getGroupByNameAndCoach(String name, String coachId) {
        return groupRepository.findByCoachIdAndName(coachId, name.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Group", name));
    }

    /**
     * Get groups by a list of IDs.
     */
    public List<Group> getGroupsByIds(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) return List.of();
        return groupRepository.findByIdIn(groupIds);
    }
}
