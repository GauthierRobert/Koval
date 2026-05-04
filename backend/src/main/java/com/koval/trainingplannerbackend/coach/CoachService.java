package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.config.audit.AuditLog;
import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.dto.MyClubRoleEntry;
import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.coach.dto.AthleteResponse;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingOrigin;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for Coach-specific operations.
 * These methods are designed to be exposed to the AI model via function calling
 * (coach role only).
 */
@Service
public class CoachService {

    private final UserRepository userRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final GroupService groupService;
    private final CoachGroupService coachGroupService;
    private final NotificationService notificationService;
    private final ReceivedTrainingService receivedTrainingService;
    private final ClubMembershipService clubMembershipService;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubRepository clubRepository;

    public CoachService(UserRepository userRepository,
            ScheduledWorkoutRepository scheduledWorkoutRepository,
            GroupService groupService,
            CoachGroupService coachGroupService,
            NotificationService notificationService,
            ReceivedTrainingService receivedTrainingService,
            ClubMembershipService clubMembershipService,
            ClubMembershipRepository clubMembershipRepository,
            ClubRepository clubRepository) {
        this.userRepository = userRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.groupService = groupService;
        this.coachGroupService = coachGroupService;
        this.notificationService = notificationService;
        this.receivedTrainingService = receivedTrainingService;
        this.clubMembershipService = clubMembershipService;
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubRepository = clubRepository;
    }

    /**
     * Assign a training to one or more athletes.
     */
    @AuditLog(action = "ASSIGN_TRAINING")
    @Transactional
    public List<ScheduledWorkout> assignTraining(
            String coachId,
            String trainingId,
            List<String> athleteIds,
            LocalDate scheduledDate,
            String notes,
            String groupId) {
        User coach = requireUser(coachId);
        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("User is not a coach: " + coachId);
        }
        Set<String> coachAthleteIds = new HashSet<>(groupService.getAthleteIdsForCoach(coachId));
        rejectUnknownAthlete(athleteIds, coachAthleteIds,
                id -> "Athlete " + id + " is not assigned to coach " + coachId);

        var origin = resolveGroupOrigin(coachId, groupId, athleteIds);
        return persistAssignments(coach, trainingId, athleteIds, scheduledDate, notes,
                ReceivedTrainingOrigin.COACH_GROUP, origin.id(), origin.name(),
                "New Training Assigned");
    }

    /**
     * Assign a training to athletes from a club context.
     */
    @Transactional
    public List<ScheduledWorkout> assignTrainingFromClub(
            String coachId,
            String trainingId,
            List<String> athleteIds,
            LocalDate scheduledDate,
            String notes,
            String clubId) {
        requireClubCoachRole(coachId, clubId);
        Set<String> activeMemberIds = Set.copyOf(clubMembershipService.getActiveMemberIds(clubId));
        rejectUnknownAthlete(athleteIds, activeMemberIds,
                id -> "Athlete " + id + " is not an active member of club " + clubId);

        User coach = requireUser(coachId);
        String clubName = clubRepository.findById(clubId).map(Club::getName).orElse("Unknown Club");
        return persistAssignments(coach, trainingId, athleteIds, scheduledDate, notes,
                ReceivedTrainingOrigin.CLUB, clubId, clubName, clubName + " — New Training");
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void requireClubCoachRole(String coachId, String clubId) {
        boolean hasRole = clubMembershipService.getMyClubRoles(coachId).stream()
                .filter(r -> r.clubId().equals(clubId))
                .anyMatch(r -> r.role() == ClubMemberRole.COACH
                        || r.role() == ClubMemberRole.ADMIN
                        || r.role() == ClubMemberRole.OWNER);
        if (!hasRole) {
            throw new ForbiddenOperationException("User does not have coach/admin/owner role in club: " + clubId);
        }
    }

    private static void rejectUnknownAthlete(List<String> athleteIds, Set<String> allowedIds,
                                             java.util.function.Function<String, String> errorMessage) {
        athleteIds.stream()
                .filter(id -> !allowedIds.contains(id))
                .findFirst()
                .ifPresent(id -> { throw new ValidationException(errorMessage.apply(id)); });
    }

    private record OriginRef(String id, String name) {}

    private OriginRef resolveGroupOrigin(String coachId, String groupId, List<String> athleteIds) {
        String name = null;
        if (groupId != null && !groupId.isBlank()) {
            try { name = groupService.getGroupById(groupId).getName(); } catch (Exception ignored) {}
        }
        if (name != null) return new OriginRef(groupId, name);

        // Infer from the first group containing any of the athletes
        Set<String> athleteIdSet = Set.copyOf(athleteIds);
        return groupService.getGroupsForCoach(coachId).stream()
                .filter(g -> g.getAthleteIds().stream().anyMatch(athleteIdSet::contains))
                .findFirst()
                .map(g -> new OriginRef(g.getId(), g.getName()))
                .orElse(new OriginRef(groupId, null));
    }

    private List<ScheduledWorkout> persistAssignments(User coach, String trainingId,
                                                      List<String> athleteIds, LocalDate scheduledDate,
                                                      String notes, ReceivedTrainingOrigin origin,
                                                      String originId, String originName,
                                                      String notificationTitle) {
        List<ScheduledWorkout> toSave = athleteIds.stream()
                .map(id -> newPendingWorkout(trainingId, id, coach.getId(), scheduledDate, notes))
                .toList();
        List<ScheduledWorkout> assignments = scheduledWorkoutRepository.saveAll(toSave);

        receivedTrainingService.createReceivedTrainings(
                trainingId, athleteIds, coach.getId(), origin, originId, originName);

        notificationService.sendToUsers(
                athleteIds,
                notificationTitle,
                coach.getDisplayName() + " assigned you a workout for " + scheduledDate,
                Map.of("type", "TRAINING_ASSIGNED",
                       "trainingId", trainingId,
                       "scheduledDate", scheduledDate.toString()),
                "workoutAssigned");
        return assignments;
    }

    /**
     * Allow any user to assign a training to themselves on a specific date.
     */
    public ScheduledWorkout selfAssignTraining(
            String userId,
            String trainingId,
            LocalDate scheduledDate,
            String notes) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return scheduledWorkoutRepository.save(
                newPendingWorkout(trainingId, userId, userId, scheduledDate, notes));
    }

    private static ScheduledWorkout newPendingWorkout(String trainingId, String athleteId,
                                                       String assignedBy, LocalDate scheduledDate, String notes) {
        ScheduledWorkout workout = new ScheduledWorkout();
        workout.setTrainingId(trainingId);
        workout.setAthleteId(athleteId);
        workout.setAssignedBy(assignedBy);
        workout.setScheduledDate(scheduledDate);
        workout.setNotes(notes);
        workout.setStatus(ScheduleStatus.PENDING);
        return workout;
    }

    /**
     * Unassign a scheduled workout.
     */
    public void unassignTraining(String scheduledWorkoutId) {
        if (!scheduledWorkoutRepository.existsById(scheduledWorkoutId)) {
            throw new ResourceNotFoundException("Scheduled workout", scheduledWorkoutId);
        }
        scheduledWorkoutRepository.deleteById(scheduledWorkoutId);
    }

    /**
     * Check whether a coach has access to a given athlete (via groups or clubs).
     */
    public boolean isCoachOfAthlete(String coachId, String athleteId) {
        // Check group-based ownership
        if (groupService.getAthleteIdsForCoach(coachId).contains(athleteId)) {
            return true;
        }
        // Check club-based ownership (coach/admin/owner in a club where athlete is a member)
        return clubMembershipService.getMyClubRoles(coachId).stream()
                .filter(r -> r.role() == ClubMemberRole.COACH
                        || r.role() == ClubMemberRole.ADMIN
                        || r.role() == ClubMemberRole.OWNER)
                .anyMatch(r -> clubMembershipService.getActiveMemberIds(r.clubId()).contains(athleteId));
    }

    /**
     * Get all athletes visible to a coach, combining group athletes and club members.
     *
     * <p>Designed to stay fast at the 150+ athlete scale: builds a single
     * athleteId → group-names lookup map (O(N+G) instead of O(N×G)) and issues
     * one batched club-membership query covering every club the coach manages
     * (instead of one query per club).
     */
    public List<AthleteResponse> getAthletes(String coachId) {
        List<User> groupAthletes = coachGroupService.getCoachAthletes(coachId);
        List<Group> coachGroups = groupService.getGroupsForCoach(coachId);

        List<MyClubRoleEntry> coachRoles = clubMembershipService.getMyClubRoles(coachId).stream()
                .filter(r -> r.role() == ClubMemberRole.COACH
                        || r.role() == ClubMemberRole.ADMIN
                        || r.role() == ClubMemberRole.OWNER)
                .toList();

        // athleteId → group names (built once, looked up O(1) per athlete below)
        Map<String, List<String>> athleteGroupNames = new HashMap<>();
        for (Group group : coachGroups) {
            for (String athleteId : group.getAthleteIds()) {
                athleteGroupNames.computeIfAbsent(athleteId, k -> new ArrayList<>()).add(group.getName());
            }
        }

        // Single batched query for club memberships across all coach-managed clubs,
        // then map clubId → clubName to attach names without re-querying.
        Map<String, String> clubIdToName = coachRoles.stream()
                .collect(Collectors.toMap(MyClubRoleEntry::clubId, MyClubRoleEntry::clubName, (a, b) -> a));
        Map<String, List<String>> userClubNames = clubIdToName.isEmpty()
                ? Map.of()
                : clubMembershipRepository
                        .findByClubIdInAndStatus(new ArrayList<>(clubIdToName.keySet()), ClubMemberStatus.ACTIVE)
                        .stream()
                        .filter(m -> !m.getUserId().equals(coachId))
                        .collect(Collectors.groupingBy(
                                ClubMembership::getUserId,
                                Collectors.mapping(m -> clubIdToName.get(m.getClubId()), Collectors.toList())));

        Set<String> groupAthleteIds = groupAthletes.stream().map(User::getId).collect(Collectors.toSet());
        Set<String> clubOnlyIds = new HashSet<>(userClubNames.keySet());
        clubOnlyIds.removeAll(groupAthleteIds);
        List<User> clubOnlyUsers = clubOnlyIds.isEmpty() ? List.of() : userRepository.findByIdIn(new ArrayList<>(clubOnlyIds));

        return Stream.concat(
                groupAthletes.stream().map(athlete -> AthleteResponse.from(athlete,
                        athleteGroupNames.getOrDefault(athlete.getId(), List.of()),
                        userClubNames.getOrDefault(athlete.getId(), List.of()), true)),
                clubOnlyUsers.stream().map(athlete -> AthleteResponse.from(athlete, List.of(),
                        userClubNames.getOrDefault(athlete.getId(), List.of()), false))
        ).toList();
    }
}
