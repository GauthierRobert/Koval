package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Verifies that a user has access to a training based on ownership, coach relationship, or club membership.
 */
@Service
public class TrainingAccessService {

    private final CoachService coachService;
    private final ReceivedTrainingService receivedTrainingService;
    private final ClubMembershipRepository membershipRepository;

    public TrainingAccessService(CoachService coachService,
                                 ReceivedTrainingService receivedTrainingService,
                                 ClubMembershipRepository membershipRepository) {
        this.coachService = coachService;
        this.receivedTrainingService = receivedTrainingService;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Verifies that the given user has access to the specified training.
     *
     * <p>Access is granted if any of the following conditions are met:</p>
     * <ul>
     *   <li>The user is the training's creator (owner).</li>
     *   <li>The user is a coach of the training's creator.</li>
     *   <li>The user is an active member of a club the training belongs to.</li>
     *   <li>The user has received the training (e.g., assigned by a coach).</li>
     * </ul>
     *
     * @param userId   the ID of the user requesting access
     * @param training the training to check access for
     * @throws AccessDeniedException if none of the access conditions are met
     */
    public void verifyAccess(String userId, Training training) {
        if (userId.equals(training.getCreatedBy())) return;
        if (coachService.isCoachOfAthlete(userId, training.getCreatedBy())) return;
        if (training.getClubIds() != null &&
                training.getClubIds().stream().anyMatch(cid -> isActiveClubMember(userId, cid))) return;
        if (receivedTrainingService.hasReceived(userId, training.getId())) return;
        throw new AccessDeniedException("You do not have access to this training");
    }

    private boolean isActiveClubMember(String userId, String clubId) {
        if (clubId == null) return false;
        return membershipRepository.findByClubIdAndUserId(clubId, userId)
                .map(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .orElse(false);
    }
}
