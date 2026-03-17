package com.koval.trainingplannerbackend.training.group;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        Optional<Group> existing = groupRepository.findByCoachIdAndName(coachId, normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        Group group = new Group();
        group.setName(normalized);
        group.setCoachId(coachId);
        group.setMaxAthletes(maxAthletes);
        group.setCreatedAt(LocalDateTime.now());
        return groupRepository.save(group);
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
        List<Group> coachGroups = groupRepository.findByCoachId(coachId);
        List<Group> modifiedGroups = new ArrayList<>();
        for (Group group : coachGroups) {
            if (group.getAthleteIds().remove(athleteId)) {
                modifiedGroups.add(group);
            }
        }
        if (!modifiedGroups.isEmpty()) {
            groupRepository.saveAll(modifiedGroups);
        }
    }

    /**
     * Get all unique athlete IDs across all groups for a coach.
     */
    public List<String> getAthleteIdsForCoach(String coachId) {
        List<Group> groups = groupRepository.findByCoachId(coachId);
        Set<String> athleteIds = new LinkedHashSet<>();
        for (Group group : groups) {
            athleteIds.addAll(group.getAthleteIds());
        }
        return new ArrayList<>(athleteIds);
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
        List<Group> groups = groupRepository.findByAthleteIdsContaining(athleteId);
        return groups.stream()
                .map(Group::getCoachId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Rename a group. Only the coach who owns it can rename.
     */
    public Group renameGroup(String groupId, String newName, String coachId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
        if (!coachId.equals(group.getCoachId())) {
            throw new ForbiddenOperationException("Only the group owner can rename this group");
        }
        group.setName(newName.toLowerCase().trim());
        return groupRepository.save(group);
    }

    /**
     * Delete a group. Only the coach who owns it can delete.
     */
    public void deleteGroup(String groupId, String coachId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
        if (!coachId.equals(group.getCoachId())) {
            throw new ForbiddenOperationException("Only the group owner can delete this group");
        }
        groupRepository.deleteById(groupId);
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
