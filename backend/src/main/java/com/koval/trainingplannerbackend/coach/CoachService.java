package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
    private final TagService tagService;

    public CoachService(UserRepository userRepository,
                        UserService userService,
                        ScheduledWorkoutRepository scheduledWorkoutRepository,
                        InviteCodeRepository inviteCodeRepository,
                        TagService tagService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.tagService = tagService;
    }

    /**
     * Assign a training to one or more athletes.
     */
    public List<ScheduledWorkout> assignTraining(
            String coachId,
            String trainingId,
            List<String> athleteIds,
            LocalDate scheduledDate,
            String notes) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));

        if (coach.getRole() != UserRole.COACH) {
            throw new IllegalStateException("User is not a coach: " + coachId);
        }

        // Verify all athletes belong to this coach via tags
        List<String> coachAthleteIds = tagService.getAthleteIdsForCoach(coachId);

        List<ScheduledWorkout> assignments = new ArrayList<>();
        for (String athleteId : athleteIds) {
            if (!coachAthleteIds.contains(athleteId)) {
                throw new IllegalStateException("Athlete " + athleteId + " is not assigned to coach " + coachId);
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
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

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
            throw new IllegalArgumentException("Scheduled workout not found: " + scheduledWorkoutId);
        }
        scheduledWorkoutRepository.deleteById(scheduledWorkoutId);
    }

    /**
     * Get an athlete's schedule within a date range.
     */
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId, LocalDate start, LocalDate end) {
        return scheduledWorkoutRepository.findByAthleteIdAndScheduledDateBetween(athleteId, start.minusDays(1), end.plusDays(1));
    }

    /**
     * Get all scheduled workouts for an athlete.
     */
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId) {
        return scheduledWorkoutRepository.findByAthleteId(athleteId);
    }

    /**
     * Get all athletes for a coach (derived from tags).
     */
    public List<User> getCoachAthletes(String coachId) {
        List<String> athleteIds = tagService.getAthleteIdsForCoach(coachId);
        if (athleteIds.isEmpty()) return List.of();
        return userRepository.findByIdIn(athleteIds);
    }

    /**
     * Remove an athlete from a coach's roster (remove from all coach tags).
     */
    public void removeAthlete(String coachId, String athleteId) {
        tagService.removeAthleteFromAllCoachTags(coachId, athleteId);
    }

    /**
     * Mark a scheduled workout as completed.
     */
    public ScheduledWorkout markCompleted(String scheduledWorkoutId, Integer tss, Double intensityFactor) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled workout not found: " + scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.COMPLETED);
        workout.setCompletedAt(LocalDateTime.now());
        if (tss != null)
            workout.setTss(tss);
        if (intensityFactor != null)
            workout.setIntensityFactor(intensityFactor);

        return scheduledWorkoutRepository.save(workout);
    }

    /**
     * Mark a scheduled workout as skipped.
     */
    public ScheduledWorkout markSkipped(String scheduledWorkoutId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled workout not found: " + scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.SKIPPED);
        return scheduledWorkoutRepository.save(workout);
    }

    /**
     * Get athletes filtered by tag for a specific coach.
     */
    public List<User> getAthletesByTag(String coachId, String tagId) {
        Tag tag = tagService.getTagById(tagId);
        if (!coachId.equals(tag.getCoachId())) {
            throw new IllegalStateException("Tag does not belong to this coach");
        }
        if (tag.getAthleteIds().isEmpty()) return List.of();
        return userRepository.findByIdIn(tag.getAthleteIds());
    }

    /**
     * Get all tags for a coach (Tag objects).
     */
    public List<Tag> getAthleteTagsForCoach(String coachId) {
        return tagService.getTagsForCoach(coachId);
    }

    /**
     * Add a tag to an athlete. Coach-only. Creates the tag if it doesn't exist, then adds athlete.
     */
    public Tag addTagToAthlete(String coachId, String athleteId, String tagName) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));
        if (coach.getRole() != UserRole.COACH) {
            throw new IllegalStateException("Only coaches can assign tags to athletes");
        }

        // Verify athlete exists
        userRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete not found: " + athleteId));

        Tag tag = tagService.getOrCreateTag(tagName, coachId);
        return tagService.addAthleteToTag(tag.getId(), athleteId);
    }

    /**
     * Remove a tag from an athlete. Coach-only.
     */
    public Tag removeTagFromAthlete(String coachId, String athleteId, String tagId) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));
        if (coach.getRole() != UserRole.COACH) {
            throw new IllegalStateException("Only coaches can modify athlete tags");
        }

        Tag tag = tagService.getTagById(tagId);
        if (!coachId.equals(tag.getCoachId())) {
            throw new IllegalStateException("Tag does not belong to this coach");
        }

        return tagService.removeAthleteFromTag(tagId, athleteId);
    }

    /**
     * Replace all tags for an athlete under this coach.
     * Removes athlete from all current coach tags, then adds to specified tags.
     */
    public List<Tag> setAthleteTags(String coachId, String athleteId, List<String> tagIds) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));
        if (coach.getRole() != UserRole.COACH) {
            throw new IllegalStateException("Only coaches can modify athlete tags");
        }

        // Remove from all current coach tags
        tagService.removeAthleteFromAllCoachTags(coachId, athleteId);

        // Add to each specified tag
        List<Tag> result = new ArrayList<>();
        for (String tagId : tagIds) {
            Tag tag = tagService.getTagById(tagId);
            if (!coachId.equals(tag.getCoachId())) {
                throw new IllegalStateException("Tag " + tagId + " does not belong to this coach");
            }
            result.add(tagService.addAthleteToTag(tagId, athleteId));
        }
        return result;
    }

    // --- Invite Code operations ---

    /**
     * Generate an invite code for a coach. Tags param contains Tag document IDs.
     */
    public InviteCode generateInviteCode(String coachId, List<String> tagIds, int maxUses, LocalDateTime expiresAt) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));

        if (coach.getRole() != UserRole.COACH) {
            throw new IllegalStateException("User is not a coach: " + coachId);
        }

        InviteCode inviteCode = new InviteCode();
        inviteCode.setCode(generateUniqueCode());
        inviteCode.setCoachId(coachId);
        inviteCode.setTags(tagIds != null ? tagIds : new ArrayList<>());
        inviteCode.setMaxUses(maxUses);
        inviteCode.setExpiresAt(expiresAt);

        return inviteCodeRepository.save(inviteCode);
    }

    /**
     * Redeem an invite code as an athlete.
     * Multi-coach is now allowed — no "already has a coach" check.
     */
    public User redeemInviteCode(String athleteId, String code) {
        User athlete = userService.getUserById(athleteId);

        InviteCode inviteCode = inviteCodeRepository.findByCode(code.toUpperCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));

        if (!inviteCode.isActive()) {
            throw new IllegalStateException("Invite code is no longer active");
        }

        if (inviteCode.getExpiresAt() != null && LocalDateTime.now().isAfter(inviteCode.getExpiresAt())) {
            throw new IllegalStateException("Invite code has expired");
        }

        if (inviteCode.getMaxUses() > 0 && inviteCode.getCurrentUses() >= inviteCode.getMaxUses()) {
            throw new IllegalStateException("Invite code has reached maximum uses");
        }

        // Add athlete to each Tag referenced by the invite code
        for (String tagId : inviteCode.getTags()) {
            tagService.addAthleteToTag(tagId, athleteId);
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
                .orElseThrow(() -> new IllegalArgumentException("Invite code not found: " + inviteCodeId));

        if (!coachId.equals(inviteCode.getCoachId())) {
            throw new IllegalStateException("Invite code does not belong to this coach");
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
        throw new IllegalStateException("Unable to generate unique invite code after 10 attempts");
    }
}
