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
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));

        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("User is not a coach: " + coachId);
        }

        // Verify all athletes belong to this coach via groups
        List<String> coachAthleteIds = groupService.getAthleteIdsForCoach(coachId);

        List<ScheduledWorkout> assignments = new ArrayList<>();
        for (String athleteId : athleteIds) {
            if (!coachAthleteIds.contains(athleteId)) {
                throw new ValidationException("Athlete " + athleteId + " is not assigned to coach " + coachId);
            }

            ScheduledWorkout workout = new ScheduledWorkout();
            workout.setTrainingId(trainingId);
            workout.setAthleteId(athleteId);
            workout.setAssignedBy(coachId);
            workout.setScheduledDate(scheduledDate);
            workout.setNotes(notes);
            workout.setStatus(ScheduleStatus.PENDING);

            assignments.add(scheduledWorkoutRepository.save(workout));
        }

        // Create ReceivedTraining entries
        String originId = groupId;
        String originName = null;
        if (groupId != null && !groupId.isBlank()) {
            try {
                Group group = groupService.getGroupById(groupId);
                originName = group.getName();
            } catch (Exception ignored) {}
        }
        if (originName == null) {
            // Infer from the first matching group
            List<Group> coachGroups = groupService.getGroupsForCoach(coachId);
            Set<String> athleteIdSet = Set.copyOf(athleteIds);
            for (Group g : coachGroups) {
                if (g.getAthleteIds().stream().anyMatch(athleteIdSet::contains)) {
                    originId = g.getId();
                    originName = g.getName();
                    break;
                }
            }
        }
        receivedTrainingService.createReceivedTrainings(
                trainingId, athleteIds, coachId,
                ReceivedTrainingOrigin.COACH_GROUP,
                originId, originName);

        notificationService.sendToUsers(
                athleteIds,
                "New Training Assigned",
                coach.getDisplayName() + " assigned you a workout for " + scheduledDate,
                Map.of("type", "TRAINING_ASSIGNED",
                       "trainingId", trainingId,
                       "scheduledDate", scheduledDate.toString()),
                "workoutAssigned");

        return assignments;
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

        // Validate coach has COACH/ADMIN/OWNER role in club
        var roles = clubMembershipService.getMyClubRoles(coachId);
        boolean hasClubRole = roles.stream()
                .filter(r -> r.clubId().equals(clubId))
                .anyMatch(r -> r.role() == ClubMemberRole.COACH
                        || r.role() == ClubMemberRole.ADMIN
                        || r.role() == ClubMemberRole.OWNER);
        if (!hasClubRole) {
            throw new ForbiddenOperationException("User does not have coach/admin/owner role in club: " + clubId);
        }

        // Validate all athletes are active club members
        List<String> activeMemberIds = clubMembershipService.getActiveMemberIds(clubId);
        for (String athleteId : athleteIds) {
            if (!activeMemberIds.contains(athleteId)) {
                throw new ValidationException("Athlete " + athleteId + " is not an active member of club " + clubId);
            }
        }

        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));

        List<ScheduledWorkout> assignments = new ArrayList<>();
        for (String athleteId : athleteIds) {
            ScheduledWorkout workout = new ScheduledWorkout();
            workout.setTrainingId(trainingId);
            workout.setAthleteId(athleteId);
            workout.setAssignedBy(coachId);
            workout.setScheduledDate(scheduledDate);
            workout.setNotes(notes);
            workout.setStatus(ScheduleStatus.PENDING);
            assignments.add(scheduledWorkoutRepository.save(workout));
        }

        // Create ReceivedTraining entries with CLUB origin
        String clubName = clubRepository.findById(clubId).map(Club::getName).orElse("Unknown Club");
        receivedTrainingService.createReceivedTrainings(
                trainingId, athleteIds, coachId,
                ReceivedTrainingOrigin.CLUB,
                clubId, clubName);

        notificationService.sendToUsers(
                athleteIds,
                clubName + " — New Training",
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

        ScheduledWorkout workout = new ScheduledWorkout();
        workout.setTrainingId(trainingId);
        workout.setAthleteId(userId);
        workout.setAssignedBy(userId);
        workout.setScheduledDate(scheduledDate);
        workout.setNotes(notes);
        workout.setStatus(ScheduleStatus.PENDING);

        return scheduledWorkoutRepository.save(workout);
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
        List<String> groupAthleteIds = groupService.getAthleteIdsForCoach(coachId);
        if (groupAthleteIds.contains(athleteId)) {
            return true;
        }
        // Check club-based ownership (coach/admin/owner in a club where athlete is a member)
        var roles = clubMembershipService.getMyClubRoles(coachId);
        for (var role : roles) {
            if (role.role() == ClubMemberRole.COACH || role.role() == ClubMemberRole.ADMIN || role.role() == ClubMemberRole.OWNER) {
                List<String> memberIds = clubMembershipService.getActiveMemberIds(role.clubId());
                if (memberIds.contains(athleteId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get all athletes visible to a coach, combining group athletes and club members.
     */
    public List<AthleteResponse> getAthletes(String coachId) {
        List<User> groupAthletes = coachGroupService.getCoachAthletes(coachId);
        List<Group> coachGroups = groupService.getGroupsForCoach(coachId);

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

        Set<String> clubOnlyIds = new HashSet<>(userClubNames.keySet());
        clubOnlyIds.removeAll(groupAthleteIds);
        List<User> clubOnlyUsers = clubOnlyIds.isEmpty() ? List.of() : userRepository.findByIdIn(new ArrayList<>(clubOnlyIds));

        List<AthleteResponse> result = new ArrayList<>();

        for (User athlete : groupAthletes) {
            List<String> athleteGroupNames = coachGroups.stream()
                    .filter(group -> group.getAthleteIds().contains(athlete.getId()))
                    .map(Group::getName)
                    .toList();
            result.add(AthleteResponse.from(athlete, athleteGroupNames,
                    userClubNames.getOrDefault(athlete.getId(), List.of()), true));
        }

        for (User athlete : clubOnlyUsers) {
            result.add(AthleteResponse.from(athlete, List.of(),
                    userClubNames.getOrDefault(athlete.getId(), List.of()), false));
        }

        return result;
    }
}
