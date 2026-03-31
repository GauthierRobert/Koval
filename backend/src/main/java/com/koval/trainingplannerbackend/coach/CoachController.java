package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.dto.MyClubRoleEntry;
import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = "*")
public class CoachController {

    private final CoachService coachService;
    private final CoachGroupService coachGroupService;
    private final GroupService groupService;
    private final ClubMembershipService clubMembershipService;
    private final ClubMembershipRepository clubMembershipRepository;
    private final UserRepository userRepository;

    public CoachController(CoachService coachService,
                           CoachGroupService coachGroupService,
                           GroupService groupService,
                           ClubMembershipService clubMembershipService,
                           ClubMembershipRepository clubMembershipRepository,
                           UserRepository userRepository) {
        this.coachService = coachService;
        this.coachGroupService = coachGroupService;
        this.groupService = groupService;
        this.clubMembershipService = clubMembershipService;
        this.clubMembershipRepository = clubMembershipRepository;
        this.userRepository = userRepository;
    }

    public record AssignmentRequest(
            String trainingId,
            List<String> athleteIds,
            LocalDate scheduledDate,
            String notes,
            Integer tss,
            Double intensityFactor,
            String clubId,
            String groupId
    ) {}

    @PostMapping("/assign")
    public ResponseEntity<List<ScheduledWorkout>> assignTraining(
            @RequestBody AssignmentRequest request) {
        String coachId = SecurityUtils.getCurrentUserId();
        List<ScheduledWorkout> assignments;
        if (request.clubId() != null && !request.clubId().isBlank()) {
            assignments = coachService.assignTrainingFromClub(
                    coachId,
                    request.trainingId(),
                    request.athleteIds(),
                    request.scheduledDate(),
                    request.notes(),
                    request.clubId());
        } else {
            assignments = coachService.assignTraining(
                    coachId,
                    request.trainingId(),
                    request.athleteIds(),
                    request.scheduledDate(),
                    request.notes(),
                    request.groupId());
        }
        return ResponseEntity.ok(assignments);
    }

    @DeleteMapping("/assign/{id}")
    public ResponseEntity<Void> unassignTraining(@PathVariable String id) {
        coachService.unassignTraining(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/athletes")
    public ResponseEntity<List<Map<String, Object>>> getAthletes() {
        String coachId = SecurityUtils.getCurrentUserId();
        List<User> groupAthletes = coachGroupService.getCoachAthletes(coachId);
        List<Group> coachGroups = groupService.getGroupsForCoach(coachId);

        // Build club membership map: userId → list of club names
        List<MyClubRoleEntry> myRoles = clubMembershipService.getMyClubRoles(coachId);
        List<MyClubRoleEntry> coachRoles = myRoles.stream()
                .filter(r -> r.role() == ClubMemberRole.COACH || r.role() == ClubMemberRole.ADMIN || r.role() == ClubMemberRole.OWNER)
                .toList();

        Set<String> groupAthleteIds = groupAthletes.stream().map(User::getId).collect(Collectors.toSet());
        Map<String, List<String>> userClubNames = new HashMap<>();

        for (var role : coachRoles) {
            List<ClubMembership> members = clubMembershipRepository.findByClubIdAndStatus(role.clubId(), ClubMemberStatus.ACTIVE);
            for (ClubMembership m : members) {
                if (!m.getUserId().equals(coachId)) {
                    userClubNames.computeIfAbsent(m.getUserId(), k -> new ArrayList<>()).add(role.clubName());
                }
            }
        }

        // Fetch club-only users (not already in group athletes)
        Set<String> clubOnlyIds = new HashSet<>(userClubNames.keySet());
        clubOnlyIds.removeAll(groupAthleteIds);
        List<User> clubOnlyUsers = clubOnlyIds.isEmpty() ? List.of() : userRepository.findByIdIn(new ArrayList<>(clubOnlyIds));

        // Enrich group athletes
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (User athlete : groupAthletes) {
            Map<String, Object> map = buildAthleteMap(athlete);
            List<String> athleteGroupNames = coachGroups.stream()
                    .filter(group -> group.getAthleteIds().contains(athlete.getId()))
                    .map(Group::getName)
                    .toList();
            map.put("groups", athleteGroupNames);
            map.put("clubs", userClubNames.getOrDefault(athlete.getId(), List.of()));
            map.put("hasCoach", true);
            enriched.add(map);
        }

        // Add club-only athletes
        for (User athlete : clubOnlyUsers) {
            Map<String, Object> map = buildAthleteMap(athlete);
            map.put("groups", List.of());
            map.put("clubs", userClubNames.getOrDefault(athlete.getId(), List.of()));
            map.put("hasCoach", false);
            enriched.add(map);
        }

        return ResponseEntity.ok(enriched);
    }

    @GetMapping(value = "/athletes", params = "page")
    public ResponseEntity<Page<Map<String, Object>>> getAthletes(Pageable pageable) {
        // Reuse the non-paginated logic, then paginate
        List<Map<String, Object>> enriched = getAthletes().getBody();
        if (enriched == null) enriched = List.of();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), enriched.size());
        List<Map<String, Object>> pageContent = start >= enriched.size() ? List.of() : enriched.subList(start, end);
        return ResponseEntity.ok(new PageImpl<>(pageContent, pageable, enriched.size()));
    }

    @DeleteMapping("/athletes/{athleteId}")
    public ResponseEntity<Void> removeAthlete(@PathVariable String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        coachGroupService.removeAthlete(coachId, athleteId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> buildAthleteMap(User athlete) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", athlete.getId());
        map.put("displayName", athlete.getDisplayName());
        map.put("profilePicture", athlete.getProfilePicture());
        map.put("role", athlete.getRole().name());
        map.put("ftp", athlete.getFtp());
        map.put("weightKg", athlete.getWeightKg());
        map.put("functionalThresholdPace", athlete.getFunctionalThresholdPace());
        map.put("criticalSwimSpeed", athlete.getCriticalSwimSpeed());
        map.put("pace5k", athlete.getPace5k());
        map.put("pace10k", athlete.getPace10k());
        map.put("paceHalfMarathon", athlete.getPaceHalfMarathon());
        map.put("paceMarathon", athlete.getPaceMarathon());
        map.put("vo2maxPower", athlete.getVo2maxPower());
        map.put("vo2maxPace", athlete.getVo2maxPace());
        map.put("customZoneReferenceValues", athlete.getCustomZoneReferenceValues());
        return map;
    }
}
