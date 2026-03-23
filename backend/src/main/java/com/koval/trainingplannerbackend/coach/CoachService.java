package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.club.ClubRepository;
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

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for Coach-specific operations.
 * These methods are designed to be exposed to the AI model via function calling
 * (coach role only).
 */
@Service
public class CoachService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserService userService;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final GroupService groupService;
    private final NotificationService notificationService;
    private final ReceivedTrainingService receivedTrainingService;
    private final ClubMembershipService clubMembershipService;
    private final ClubRepository clubRepository;

    public CoachService(UserRepository userRepository,
            UserService userService,
            ScheduledWorkoutRepository scheduledWorkoutRepository,
            InviteCodeRepository inviteCodeRepository,
            GroupService groupService,
            NotificationService notificationService,
            ReceivedTrainingService receivedTrainingService,
            ClubMembershipService clubMembershipService,
            ClubRepository clubRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.groupService = groupService;
        this.notificationService = notificationService;
        this.receivedTrainingService = receivedTrainingService;
        this.clubMembershipService = clubMembershipService;
        this.clubRepository = clubRepository;
    }

    /**
     * Assign a training to one or more athletes.
     */
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
                       "scheduledDate", scheduledDate.toString()));

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
                "New Training Assigned",
                coach.getDisplayName() + " assigned you a workout for " + scheduledDate,
                Map.of("type", "TRAINING_ASSIGNED",
                       "trainingId", trainingId,
                       "scheduledDate", scheduledDate.toString()));

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
     * Get an athlete's schedule within a date range.
     */
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId, LocalDate start, LocalDate end) {
        return scheduledWorkoutRepository.findByAthleteIdAndScheduledDateBetween(athleteId, start.minusDays(1),
                end.plusDays(1));
    }

    /**
     * Get all scheduled workouts for an athlete.
     */
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId) {
        return scheduledWorkoutRepository.findByAthleteId(athleteId);
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
     * Remove an athlete from a coach's roster (remove from all coach groups).
     */
    public void removeAthlete(String coachId, String athleteId) {
        groupService.removeAthleteFromAllCoachGroups(coachId, athleteId);
    }

    /**
     * Mark a scheduled workout as completed.
     */
    public ScheduledWorkout markCompleted(String scheduledWorkoutId, Integer tss, Double intensityFactor,
            String sessionId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.COMPLETED);
        workout.setCompletedAt(LocalDateTime.now());
        if (tss != null)
            workout.setTss(tss);
        if (intensityFactor != null)
            workout.setIntensityFactor(intensityFactor);
        if (sessionId != null)
            workout.setSessionId(sessionId);

        return scheduledWorkoutRepository.save(workout);
    }

    public ScheduledWorkout markCompleted(String scheduledWorkoutId, Integer tss, Double intensityFactor) {
        return markCompleted(scheduledWorkoutId, tss, intensityFactor, null);
    }

    /**
     * Mark a scheduled workout as skipped.
     */
    public ScheduledWorkout markSkipped(String scheduledWorkoutId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.SKIPPED);
        return scheduledWorkoutRepository.save(workout);
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
        List<Group> result = new ArrayList<>();
        for (String groupId : groupIds) {
            Group group = groupService.getGroupById(groupId);
            if (!coachId.equals(group.getCoachId())) {
                throw new ForbiddenOperationException("Group " + groupId + " does not belong to this coach");
            }
            result.add(groupService.addAthleteToGroup(groupId, athleteId));
        }
        return result;
    }

    // --- Invite Code operations ---

    /**
     * Generate an invite code for a coach. Groups param contains Group document IDs.
     */
    public InviteCode generateInviteCode(String coachId, List<String> groupIds, int maxUses, LocalDateTime expiresAt, String customCode) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));

        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("User is not a coach: " + coachId);
        }

        InviteCode inviteCode = new InviteCode();
        inviteCode.setCode(customCode != null && !customCode.isBlank()
                ? customCode.toUpperCase().trim()
                : generateUniqueCode());
        inviteCode.setCoachId(coachId);
        inviteCode.setGroupIds(groupIds != null ? groupIds : new ArrayList<>());
        inviteCode.setMaxUses(maxUses);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setType("GROUP");

        return inviteCodeRepository.save(inviteCode);
    }

    /**
     * Redeem an invite code as an athlete.
     * Multi-coach is now allowed — no "already has a coach" check.
     */
    @Transactional
    public User redeemInviteCode(String athleteId, String code) {
        User athlete = userService.getUserById(athleteId);

        InviteCode inviteCode = inviteCodeRepository.findByCode(code.toUpperCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code"));

        if (!inviteCode.isActive()) {
            throw new ValidationException("Invite code is no longer active");
        }

        if (inviteCode.getExpiresAt() != null && LocalDateTime.now().isAfter(inviteCode.getExpiresAt())) {
            throw new ValidationException("Invite code has expired");
        }

        if (inviteCode.getMaxUses() > 0 && inviteCode.getCurrentUses() >= inviteCode.getMaxUses()) {
            throw new ValidationException("Invite code has reached maximum uses");
        }

        // Add athlete to each Group referenced by the invite code
        for (String groupId : inviteCode.getGroupIds()) {
            groupService.addAthleteToGroup(groupId, athleteId);
        }

        // Increment usage
        inviteCode.setCurrentUses(inviteCode.getCurrentUses() + 1);
        inviteCodeRepository.save(inviteCode);

        return userRepository.findById(athleteId).orElse(athlete);
    }

    /**
     * Get all invite codes for a coach.
     */
    public List<InviteCode> getInviteCodes(String coachId) {
        return inviteCodeRepository.findByCoachId(coachId);
    }

    /**
     * Deactivate an invite code.
     */
    public void deactivateInviteCode(String coachId, String inviteCodeId) {
        InviteCode inviteCode = inviteCodeRepository.findById(inviteCodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code", inviteCodeId));

        if (!coachId.equals(inviteCode.getCoachId())) {
            throw new ForbiddenOperationException("Invite code does not belong to this coach");
        }

        inviteCode.setActive(false);
        inviteCodeRepository.save(inviteCode);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (inviteCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ValidationException("Unable to generate unique invite code after 10 attempts");
    }
}
