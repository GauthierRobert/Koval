package com.koval.trainingplannerbackend.club;

import org.springframework.stereotype.Service;

@Service
public class ClubAuthorizationService {

    private final ClubMembershipRepository membershipRepository;

    public ClubAuthorizationService(ClubMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public ClubMembership requireMembership(String userId, String clubId) {
        return membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
    }

    public ClubMembership requireActiveMember(String userId, String clubId) {
        ClubMembership m = requireMembership(userId, clubId);
        if (m.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new IllegalStateException("Active membership required");
        }
        return m;
    }

    public ClubMembership requireAdminOrOwner(String userId, String clubId) {
        ClubMembership m = requireMembership(userId, clubId);
        if (m.getRole() != ClubMemberRole.OWNER && m.getRole() != ClubMemberRole.ADMIN) {
            throw new IllegalStateException("Admin or owner role required");
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
            throw new IllegalStateException("Coach, admin, or owner role required");
        }
        if (m.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new IllegalStateException("Active membership required");
        }
        return m;
    }
}
