package com.koval.trainingplannerbackend.club.membership;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;

@Service
public class ClubAuthorizationService {

    private final ClubMembershipRepository membershipRepository;

    public ClubAuthorizationService(ClubMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public ClubMembership requireMembership(String userId, String clubId) {
        return membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "Not a member of this club", "CLUB_NOT_A_MEMBER"));
    }

    public ClubMembership requireActiveMember(String userId, String clubId) {
        ClubMembership m = requireMembership(userId, clubId);
        if (m.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new ForbiddenOperationException(
                    "Active membership required", "CLUB_MEMBERSHIP_NOT_ACTIVE");
        }
        return m;
    }

    public ClubMembership requireAdminOrOwner(String userId, String clubId) {
        ClubMembership m = requireMembership(userId, clubId);
        if (m.getRole() != ClubMemberRole.OWNER && m.getRole() != ClubMemberRole.ADMIN) {
            throw new ForbiddenOperationException(
                    "Admin or owner role required", "CLUB_INSUFFICIENT_ROLE");
        }
        return m;
    }

    public boolean isAdminOrCoach(String userId, String clubId) {
        return membershipRepository.findByClubIdAndUserId(clubId, userId)
                .map(m -> m.getStatus() == ClubMemberStatus.ACTIVE && m.getRole() != ClubMemberRole.MEMBER)
                .orElse(false);
    }

    public ClubMembership requireAdminOrCoach(String userId, String clubId) {
        ClubMembership m = requireMembership(userId, clubId);
        if (m.getRole() == ClubMemberRole.MEMBER) {
            throw new ForbiddenOperationException(
                    "Coach, admin, or owner role required", "CLUB_INSUFFICIENT_ROLE");
        }
        if (m.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new ForbiddenOperationException(
                    "Active membership required", "CLUB_MEMBERSHIP_NOT_ACTIVE");
        }
        return m;
    }
}
