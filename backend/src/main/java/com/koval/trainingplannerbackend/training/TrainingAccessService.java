package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.club.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.ClubMembershipRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.received.ReceivedTrainingService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

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

    public void verifyAccess(String userId, Training training) {
        if (userId.equals(training.getCreatedBy())) return;
        if (coachService.isCoachOfAthlete(userId, training.getCreatedBy())) return;
        if (training.getClubIds() != null) {
            for (String cid : training.getClubIds()) {
                if (isActiveClubMember(userId, cid)) return;
            }
        }
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
