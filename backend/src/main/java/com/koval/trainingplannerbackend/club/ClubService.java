package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.chat.ChatMemberRole;
import com.koval.trainingplannerbackend.chat.ChatRoom;
import com.koval.trainingplannerbackend.chat.ChatRoomService;
import com.koval.trainingplannerbackend.chat.MembershipSource;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.activity.ClubActivityType;
import com.koval.trainingplannerbackend.club.dto.ClubDetailResponse;
import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;
import com.koval.trainingplannerbackend.club.dto.CreateClubRequest;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubActivityService clubActivityService;
    private final ClubInviteCodeService clubInviteCodeService;
    private final ChatRoomService chatRoomService;

    public ClubService(ClubRepository clubRepository,
                       ClubMembershipRepository membershipRepository,
                       ClubActivityService clubActivityService,
                       ClubInviteCodeService clubInviteCodeService,
                       ChatRoomService chatRoomService) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.clubActivityService = clubActivityService;
        this.clubInviteCodeService = clubInviteCodeService;
        this.chatRoomService = chatRoomService;
    }

    // --- Club CRUD ---

    @CacheEvict(value = "userClubs", key = "#userId")
    @Transactional
    public Club createClub(String userId, CreateClubRequest req) {
        Club club = new Club();
        club.setName(req.name());
        club.setDescription(req.description());
        club.setLocation(req.location());
        club.setLogoUrl(req.logoUrl());
        club.setVisibility(req.visibility() != null ? req.visibility() : ClubVisibility.PUBLIC);
        club.setOwnerId(userId);
        club.setMemberCount(1);
        club.setCreatedAt(LocalDateTime.now());
        club = clubRepository.save(club);

        ClubMembership membership = new ClubMembership();
        membership.setClubId(club.getId());
        membership.setUserId(userId);
        membership.setRole(ClubMemberRole.OWNER);
        membership.setStatus(ClubMemberStatus.ACTIVE);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setRequestedAt(LocalDateTime.now());
        membershipRepository.save(membership);

        clubActivityService.emitActivity(club.getId(), ClubActivityType.MEMBER_JOINED, userId, null, null);

        // Auto-generate default invite code for the club
        clubInviteCodeService.generateInviteCode(userId, club.getId(), null, 0, null);

        // Provision the club chat room and auto-join the owner as admin.
        ChatRoom clubRoom = chatRoomService.getOrCreateClubRoom(club.getId());
        chatRoomService.ensureMembership(clubRoom, userId, MembershipSource.AUTO, ChatMemberRole.ADMIN);

        return club;
    }

    @Cacheable(value = "userClubs", key = "#userId")
    public List<ClubSummaryResponse> getUserClubs(String userId) {
        List<ClubMembership> memberships = membershipRepository.findByUserId(userId);
        List<String> clubIds = memberships.stream().map(ClubMembership::getClubId).toList();
        Map<String, Club> clubMap = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, c -> c));
        Map<String, ClubMembership> membershipByClubId = memberships.stream()
                .collect(Collectors.toMap(ClubMembership::getClubId, m -> m));

        return clubIds.stream()
                .filter(clubMap::containsKey)
                .map(id -> {
                    Club c = clubMap.get(id);
                    ClubMembership m = membershipByClubId.get(id);
                    return new ClubSummaryResponse(
                            c.getId(), c.getName(), c.getDescription(), c.getLogoUrl(),
                            c.getVisibility(),
                            m.getStatus().name() + "_" + m.getRole().name());
                })
                .collect(Collectors.toList());
    }

    public List<Club> browsePublicClubs(Pageable pageable) {
        return clubRepository.findByVisibility(ClubVisibility.PUBLIC, pageable);
    }

    public ClubDetailResponse getClubDetail(String clubId, String userId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
        Optional<ClubMembership> membership = membershipRepository.findByClubIdAndUserId(clubId, userId);
        String membershipStatus = membership.map(m -> m.getStatus().name()).orElse(null);
        ClubMemberRole memberRole = membership.map(ClubMembership::getRole).orElse(null);
        return new ClubDetailResponse(
                club.getId(), club.getName(), club.getDescription(), club.getLocation(),
                club.getLogoUrl(), club.getVisibility(), club.getMemberCount(),
                club.getOwnerId(), membershipStatus, memberRole, club.getCreatedAt());
    }

}
