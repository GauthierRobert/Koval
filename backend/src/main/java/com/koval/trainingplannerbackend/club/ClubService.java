package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
public class ClubService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubActivityRepository activityRepository;
    private final CompletedSessionRepository completedSessionRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final UserService userService;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubInviteCodeRepository clubInviteCodeRepository;
    private final NotificationService notificationService;
    private final TrainingService trainingService;

    public ClubService(ClubRepository clubRepository,
                       ClubMembershipRepository membershipRepository,
                       ClubTrainingSessionRepository sessionRepository,
                       ClubActivityRepository activityRepository,
                       CompletedSessionRepository completedSessionRepository,
                       RaceGoalRepository raceGoalRepository,
                       UserService userService,
                       ClubGroupRepository clubGroupRepository,
                       ClubInviteCodeRepository clubInviteCodeRepository,
                       NotificationService notificationService,
                       TrainingService trainingService) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
        this.completedSessionRepository = completedSessionRepository;
        this.raceGoalRepository = raceGoalRepository;
        this.userService = userService;
        this.clubGroupRepository = clubGroupRepository;
        this.clubInviteCodeRepository = clubInviteCodeRepository;
        this.notificationService = notificationService;
        this.trainingService = trainingService;
    }

    // --- Club CRUD ---

    public Club createClub(String userId, ClubController.CreateClubRequest req) {
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

        emitActivity(club.getId(), ClubActivityType.MEMBER_JOINED, userId, null, null);

        // Auto-generate default invite code for the club
        ClubInviteCode autoCode = new ClubInviteCode();
        autoCode.setCode(generateUniqueClubCode());
        autoCode.setClubId(club.getId());
        autoCode.setCreatedBy(userId);
        autoCode.setMaxUses(0);
        autoCode.setType("CLUB");
        autoCode.setCreatedAt(LocalDateTime.now());
        clubInviteCodeRepository.save(autoCode);

        return club;
    }

    public List<ClubController.ClubSummaryResponse> getUserClubs(String userId) {
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
                    return new ClubController.ClubSummaryResponse(
                            c.getId(), c.getName(), c.getDescription(), c.getLogoUrl(),
                            c.getVisibility(), c.getMemberCount(),
                            m.getStatus().name() + "_" + m.getRole().name());
                })
                .collect(Collectors.toList());
    }

    public List<Club> browsePublicClubs(Pageable pageable) {
        return clubRepository.findByVisibility(ClubVisibility.PUBLIC);
    }

    public ClubController.ClubDetailResponse getClubDetail(String clubId, String userId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        Optional<ClubMembership> membership = membershipRepository.findByClubIdAndUserId(clubId, userId);
        String membershipStatus = membership.map(m -> m.getStatus().name()).orElse(null);
        ClubMemberRole memberRole = membership.map(ClubMembership::getRole).orElse(null);
        return new ClubController.ClubDetailResponse(
                club.getId(), club.getName(), club.getDescription(), club.getLocation(),
                club.getLogoUrl(), club.getVisibility(), club.getMemberCount(),
                club.getOwnerId(), membershipStatus, memberRole, club.getCreatedAt());
    }

    public ClubMembership joinClub(String userId, String clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));

        Optional<ClubMembership> existing = membershipRepository.findByClubIdAndUserId(clubId, userId);
        if (existing.isPresent()) {
            throw new IllegalStateException("Already a member or pending");
        }

        ClubMembership membership = new ClubMembership();
        membership.setClubId(clubId);
        membership.setUserId(userId);
        membership.setRole(ClubMemberRole.MEMBER);
        membership.setRequestedAt(LocalDateTime.now());

        if (club.getVisibility() == ClubVisibility.PUBLIC) {
            membership.setStatus(ClubMemberStatus.ACTIVE);
            membership.setJoinedAt(LocalDateTime.now());
            club.setMemberCount(club.getMemberCount() + 1);
            clubRepository.save(club);
            emitActivity(clubId, ClubActivityType.MEMBER_JOINED, userId, null, null);
        } else {
            membership.setStatus(ClubMemberStatus.PENDING);
        }
        return membershipRepository.save(membership);
    }

    public void leaveClub(String userId, String clubId) {
        ClubMembership membership = membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Not a member"));

        if (membership.getRole() == ClubMemberRole.OWNER) {
            throw new IllegalStateException("Owner cannot leave the club");
        }

        if (membership.getStatus() == ClubMemberStatus.ACTIVE) {
            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new IllegalArgumentException("Club not found"));
            club.setMemberCount(Math.max(0, club.getMemberCount() - 1));
            clubRepository.save(club);
            emitActivity(clubId, ClubActivityType.MEMBER_LEFT, userId, null, null);
        }
        membershipRepository.delete(membership);
    }

    public ClubMembership approveRequest(String adminId, String membershipId) {
        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        validateAdminRole(adminId, target.getClubId());

        target.setStatus(ClubMemberStatus.ACTIVE);
        target.setJoinedAt(LocalDateTime.now());
        membershipRepository.save(target);

        Club club = clubRepository.findById(target.getClubId())
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        club.setMemberCount(club.getMemberCount() + 1);
        clubRepository.save(club);
        emitActivity(target.getClubId(), ClubActivityType.MEMBER_JOINED, target.getUserId(), null, null);
        return target;
    }

    public void rejectRequest(String adminId, String membershipId) {
        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        validateAdminRole(adminId, target.getClubId());
        membershipRepository.delete(target);
    }

    public List<ClubController.ClubMemberResponse> getMembers(String clubId) {
        List<ClubMembership> memberships = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);

        // Build userId → List<groupName> map from all club groups
        List<ClubGroup> allGroups = clubGroupRepository.findByClubId(clubId);
        Map<String, List<String>> userGroupsMap = new HashMap<>();
        for (ClubGroup group : allGroups) {
            for (String memberId : group.getMemberIds()) {
                userGroupsMap.computeIfAbsent(memberId, k -> new ArrayList<>()).add(group.getName());
            }
        }

        return memberships.stream().map(m -> {
            User user = userService.findById(m.getUserId()).orElse(null);
            String displayName = user != null ? user.getDisplayName() : m.getUserId();
            String pic = user != null ? user.getProfilePicture() : null;
            List<String> tags = userGroupsMap.getOrDefault(m.getUserId(), List.of());
            return new ClubController.ClubMemberResponse(
                    m.getId(), m.getUserId(), displayName, pic, m.getRole(), m.getJoinedAt(), tags);
        }).collect(Collectors.toList());
    }

    public List<ClubController.ClubMemberResponse> getPendingRequests(String adminId, String clubId) {
        validateAdminRole(adminId, clubId);
        List<ClubMembership> pending = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.PENDING);
        return pending.stream().map(m -> {
            User user = userService.findById(m.getUserId()).orElse(null);
            String displayName = user != null ? user.getDisplayName() : m.getUserId();
            String pic = user != null ? user.getProfilePicture() : null;
            return new ClubController.ClubMemberResponse(
                    m.getId(), m.getUserId(), displayName, pic, m.getRole(), m.getRequestedAt(), List.of());
        }).collect(Collectors.toList());
    }

    // --- Role management ---

    public ClubMembership updateMemberRole(String callerId, String clubId, String membershipId, ClubMemberRole newRole) {
        ClubMembership caller = membershipRepository.findByClubIdAndUserId(clubId, callerId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (caller.getRole() != ClubMemberRole.OWNER && caller.getRole() != ClubMemberRole.ADMIN) {
            throw new IllegalStateException("Only owner or admin can change member roles");
        }
        if (newRole == ClubMemberRole.OWNER) {
            throw new IllegalStateException("Cannot promote a member to OWNER");
        }
        // Only the owner can promote to ADMIN
        if (newRole == ClubMemberRole.ADMIN && caller.getRole() != ClubMemberRole.OWNER) {
            throw new IllegalStateException("Only the owner can promote members to ADMIN");
        }

        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        if (target.getRole() == ClubMemberRole.OWNER) {
            throw new IllegalStateException("Cannot change the owner's role");
        }
        // Admin cannot change another admin's role
        if (target.getRole() == ClubMemberRole.ADMIN && caller.getRole() != ClubMemberRole.OWNER) {
            throw new IllegalStateException("Only the owner can change an admin's role");
        }
        target.setRole(newRole);
        return membershipRepository.save(target);
    }

    // --- Scoped roles ---

    public List<ClubController.MyClubRoleEntry> getMyClubRoles(String userId) {
        List<ClubMembership> memberships = membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .collect(Collectors.toList());
        List<String> clubIds = memberships.stream().map(ClubMembership::getClubId).toList();
        Map<String, Club> clubMap = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, c -> c));

        return memberships.stream()
                .filter(m -> clubMap.containsKey(m.getClubId()))
                .map(m -> {
                    Club c = clubMap.get(m.getClubId());
                    return new ClubController.MyClubRoleEntry(c.getId(), c.getName(), m.getRole());
                })
                .collect(Collectors.toList());
    }

    // --- Groups ---

    public ClubGroup createGroup(String adminId, String clubId, String name) {
        validateAdminOrOwner(adminId, clubId);
        if (clubGroupRepository.findByClubIdAndName(clubId, name).isPresent()) {
            throw new IllegalStateException("Group with this name already exists in the club");
        }
        ClubGroup group = new ClubGroup();
        group.setClubId(clubId);
        group.setName(name);
        group.setCreatedAt(LocalDateTime.now());
        group = clubGroupRepository.save(group);

        // Auto-generate invite code for the group
        ClubInviteCode autoCode = new ClubInviteCode();
        autoCode.setCode(generateUniqueClubCode());
        autoCode.setClubId(clubId);
        autoCode.setCreatedBy(adminId);
        autoCode.setClubGroupId(group.getId());
        autoCode.setMaxUses(0);
        autoCode.setType("CLUB");
        autoCode.setCreatedAt(LocalDateTime.now());
        clubInviteCodeRepository.save(autoCode);

        return group;
    }

    public List<ClubGroup> listGroups(String clubId) {
        return clubGroupRepository.findByClubId(clubId);
    }

    public void deleteGroup(String adminId, String clubId, String groupId) {
        validateAdminOrOwner(adminId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        clubGroupRepository.delete(group);
    }

    public ClubGroup addMemberToGroup(String adminId, String clubId, String groupId, String targetUserId) {
        validateAdminOrOwner(adminId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        // Validate target is an active member
        ClubMembership targetMembership = membershipRepository.findByClubIdAndUserId(clubId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this club"));
        if (targetMembership.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new IllegalStateException("User must be an active member to be added to a group");
        }
        if (!group.getMemberIds().contains(targetUserId)) {
            group.getMemberIds().add(targetUserId);
            clubGroupRepository.save(group);
        }
        return group;
    }

    public ClubGroup removeMemberFromGroup(String adminId, String clubId, String groupId, String targetUserId) {
        validateAdminOrOwner(adminId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        group.getMemberIds().remove(targetUserId);
        return clubGroupRepository.save(group);
    }

    // --- Self-service group join/leave ---

    public ClubGroup joinGroupSelf(String userId, String clubId, String groupId) {
        validateActiveMember(userId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        if (!group.getMemberIds().contains(userId)) {
            group.getMemberIds().add(userId);
            clubGroupRepository.save(group);
        }
        return group;
    }

    public ClubGroup leaveGroupSelf(String userId, String clubId, String groupId) {
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        group.getMemberIds().remove(userId);
        return clubGroupRepository.save(group);
    }

    // --- Sessions ---

    public ClubTrainingSession createSession(String userId, String clubId, ClubController.CreateSessionRequest req) {
        validateActiveMember(userId, clubId);
        validateAdminOrCoach(userId, clubId);

        ClubTrainingSession session = new ClubTrainingSession();
        session.setClubId(clubId);
        session.setCreatedBy(userId);
        session.setTitle(req.title());
        session.setSport(req.sport());
        session.setScheduledAt(req.scheduledAt());
        session.setLocation(req.location());
        session.setDescription(req.description());
        session.setLinkedTrainingId(req.linkedTrainingId());
        session.setMaxParticipants(req.maxParticipants());
        session.setDurationMinutes(req.durationMinutes());
        session.setClubGroupId(req.clubGroupId());
        session.setOpenToAll(req.openToAll());
        session.setOpenToAllDelayValue(req.openToAllDelayValue());
        session.setOpenToAllDelayUnit(req.openToAllDelayUnit());
        session.setResponsibleCoachId(req.responsibleCoachId());
        session.setCreatedAt(LocalDateTime.now());
        enrichFromLinkedTraining(session);
        session = sessionRepository.save(session);

        emitActivity(clubId, ClubActivityType.SESSION_CREATED, userId, session.getId(), session.getTitle());
        return session;
    }

    public List<ClubTrainingSession> listSessions(String clubId) {
        return sessionRepository.findByClubIdOrderByScheduledAtDesc(clubId);
    }

    public List<ClubTrainingSession> listSessions(String clubId, LocalDateTime from, LocalDateTime to) {
        return sessionRepository.findByClubIdAndScheduledAtBetween(clubId, from, to);
    }

    public ClubTrainingSession joinSession(String userId, String sessionId) {
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (session.getParticipantIds().contains(userId)) {
            return session;
        }
        if (session.isOnWaitingList(userId)) {
            return session;
        }
        if (!session.isFull()) {
            session.getParticipantIds().add(userId);
            sessionRepository.save(session);
            emitActivity(session.getClubId(), ClubActivityType.SESSION_JOINED, userId, sessionId, session.getTitle());
        } else {
            session.getWaitingList().add(new WaitingListEntry(userId, LocalDateTime.now()));
            sessionRepository.save(session);
            emitActivity(session.getClubId(), ClubActivityType.WAITING_LIST_JOINED, userId, sessionId, session.getTitle());
        }
        return session;
    }

    public ClubTrainingSession cancelSessionParticipation(String userId, String sessionId) {
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (session.getParticipantIds().remove(userId)) {
            if (session.getMaxParticipants() != null && !session.getWaitingList().isEmpty()) {
                promoteNextFromWaitingList(session);
            }
        } else {
            session.getWaitingList().removeIf(e -> e.userId().equals(userId));
        }
        return sessionRepository.save(session);
    }

    private void promoteNextFromWaitingList(ClubTrainingSession session) {
        if (session.getWaitingList().isEmpty()) return;
        WaitingListEntry promoted = session.getWaitingList().remove(0);
        session.getParticipantIds().add(promoted.userId());

        notificationService.sendToUsers(
                List.of(promoted.userId()),
                "You're In!",
                "A spot opened in " + session.getTitle() + ". You've been automatically added.",
                Map.of("type", "WAITING_LIST_PROMOTED",
                       "clubId", session.getClubId(),
                       "sessionId", session.getId()));
    }

    public ClubTrainingSession linkTrainingToSession(String userId, String clubId, String sessionId, String trainingId) {
        validateAdminOrCoach(userId, clubId);
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        session.setLinkedTrainingId(trainingId);
        enrichFromLinkedTraining(session);
        return sessionRepository.save(session);
    }

    // --- Feed ---

    public List<ClubController.ClubActivityResponse> getActivityFeed(String clubId, Pageable pageable) {
        List<ClubActivity> activities = activityRepository.findByClubIdOrderByOccurredAtDesc(clubId, pageable);
        return activities.stream().map(a -> {
            User actor = userService.findById(a.getActorId()).orElse(null);
            String actorName = actor != null ? actor.getDisplayName() : a.getActorId();
            return new ClubController.ClubActivityResponse(
                    a.getId(), a.getType(), a.getActorId(), actorName,
                    a.getTargetId(), a.getTargetTitle(), a.getOccurredAt());
        }).collect(Collectors.toList());
    }

    // --- Stats ---

    public ClubController.ClubWeeklyStatsResponse getWeeklyStats(String clubId) {
        List<String> memberIds = getActiveMemberIds(clubId);
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<CompletedSession> sessions = completedSessionRepository
                .findByUserIdInAndCompletedAtBetween(memberIds, weekStart, weekEnd);

        double swimKm = 0, bikeKm = 0, runKm = 0;
        for (CompletedSession s : sessions) {
            double dist = s.getBlockSummaries() != null
                    ? s.getBlockSummaries().stream()
                    .filter(b -> b.distanceMeters() != null)
                    .mapToDouble(CompletedSession.BlockSummary::distanceMeters).sum()
                    : 0;
            String sport = s.getSportType();
            if ("SWIMMING".equalsIgnoreCase(sport)) swimKm += dist / 1000.0;
            else if ("CYCLING".equalsIgnoreCase(sport)) bikeKm += dist / 1000.0;
            else if ("RUNNING".equalsIgnoreCase(sport)) runKm += dist / 1000.0;
        }
        return new ClubController.ClubWeeklyStatsResponse(swimKm, bikeKm, runKm, sessions.size(), memberIds.size());
    }

    public List<ClubController.LeaderboardEntry> getLeaderboard(String clubId) {
        List<String> memberIds = getActiveMemberIds(clubId);
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<CompletedSession> sessions = completedSessionRepository
                .findByUserIdInAndCompletedAtBetween(memberIds, weekStart, weekEnd);

        Map<String, Double> tssMap = new LinkedHashMap<>();
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (CompletedSession s : sessions) {
            String uid = s.getUserId();
            tssMap.merge(uid, s.getTss() != null ? s.getTss() : 0.0, Double::sum);
            countMap.merge(uid, 1, Integer::sum);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(tssMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<ClubController.LeaderboardEntry> leaderboard = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            String uid = sorted.get(i).getKey();
            User user = userService.findById(uid).orElse(null);
            leaderboard.add(new ClubController.LeaderboardEntry(
                    uid,
                    user != null ? user.getDisplayName() : uid,
                    user != null ? user.getProfilePicture() : null,
                    sorted.get(i).getValue(),
                    countMap.getOrDefault(uid, 0),
                    i + 1));
        }
        return leaderboard;
    }

    public List<ClubController.ClubRaceGoalResponse> getRaceGoals(String clubId) {
        List<String> memberIds = getActiveMemberIds(clubId);
        List<RaceGoal> goals = raceGoalRepository.findByAthleteIdInOrderByRaceDateAsc(memberIds);
        List<ClubTrainingSession> sessions = sessionRepository.findByClubIdOrderByScheduledAtDesc(clubId);

        return goals.stream().map(g -> {
            boolean hasSession = sessions.stream().anyMatch(s ->
                    s.getScheduledAt() != null &&
                    s.getScheduledAt().toLocalDate().isAfter(LocalDate.now()) &&
                    s.getScheduledAt().toLocalDate().isBefore(g.getRaceDate().plusDays(1)));
            return new ClubController.ClubRaceGoalResponse(g, hasSession);
        }).collect(Collectors.toList());
    }

    // --- Invite Codes ---

    public ClubInviteCode generateInviteCode(String userId, String clubId, String clubGroupId,
                                              int maxUses, LocalDateTime expiresAt) {
        validateAdminOrCoach(userId, clubId);

        // If a group is specified, validate it belongs to this club
        if (clubGroupId != null && !clubGroupId.isBlank()) {
            ClubGroup group = clubGroupRepository.findById(clubGroupId)
                    .orElseThrow(() -> new IllegalArgumentException("Club group not found"));
            if (!group.getClubId().equals(clubId)) {
                throw new IllegalArgumentException("Group does not belong to this club");
            }
        }

        ClubInviteCode inviteCode = new ClubInviteCode();
        inviteCode.setCode(generateUniqueClubCode());
        inviteCode.setClubId(clubId);
        inviteCode.setCreatedBy(userId);
        inviteCode.setClubGroupId(clubGroupId != null && !clubGroupId.isBlank() ? clubGroupId : null);
        inviteCode.setMaxUses(maxUses);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setType("CLUB");
        inviteCode.setCreatedAt(LocalDateTime.now());

        return clubInviteCodeRepository.save(inviteCode);
    }

    public ClubMembership redeemClubInviteCode(String userId, String code) {
        ClubInviteCode inviteCode = clubInviteCodeRepository.findByCode(code.toUpperCase().trim())
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

        String clubId = inviteCode.getClubId();

        // Join the club if not already a member
        Optional<ClubMembership> existing = membershipRepository.findByClubIdAndUserId(clubId, userId);
        ClubMembership membership;
        if (existing.isPresent()) {
            membership = existing.get();
            // If pending, approve automatically
            if (membership.getStatus() == ClubMemberStatus.PENDING) {
                membership.setStatus(ClubMemberStatus.ACTIVE);
                membership.setJoinedAt(LocalDateTime.now());
                membershipRepository.save(membership);
                Club club = clubRepository.findById(clubId)
                        .orElseThrow(() -> new IllegalArgumentException("Club not found"));
                club.setMemberCount(club.getMemberCount() + 1);
                clubRepository.save(club);
                emitActivity(clubId, ClubActivityType.MEMBER_JOINED, userId, null, null);
            }
        } else {
            // Create new active membership
            membership = new ClubMembership();
            membership.setClubId(clubId);
            membership.setUserId(userId);
            membership.setRole(ClubMemberRole.MEMBER);
            membership.setStatus(ClubMemberStatus.ACTIVE);
            membership.setJoinedAt(LocalDateTime.now());
            membership.setRequestedAt(LocalDateTime.now());
            membership = membershipRepository.save(membership);

            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new IllegalArgumentException("Club not found"));
            club.setMemberCount(club.getMemberCount() + 1);
            clubRepository.save(club);
            emitActivity(clubId, ClubActivityType.MEMBER_JOINED, userId, null, null);
        }

        // If the invite code targets a club group, add the user to it
        if (inviteCode.getClubGroupId() != null) {
            ClubGroup group = clubGroupRepository.findById(inviteCode.getClubGroupId()).orElse(null);
            if (group != null && !group.getMemberIds().contains(userId)) {
                group.getMemberIds().add(userId);
                clubGroupRepository.save(group);
            }
        }

        // Increment usage
        inviteCode.setCurrentUses(inviteCode.getCurrentUses() + 1);
        clubInviteCodeRepository.save(inviteCode);

        return membership;
    }

    public List<ClubInviteCode> getClubInviteCodes(String userId, String clubId) {
        validateAdminOrCoach(userId, clubId);
        return clubInviteCodeRepository.findByClubId(clubId);
    }

    public void deactivateClubInviteCode(String userId, String clubId, String codeId) {
        validateAdminOrCoach(userId, clubId);
        ClubInviteCode inviteCode = clubInviteCodeRepository.findById(codeId)
                .orElseThrow(() -> new IllegalArgumentException("Invite code not found"));
        if (!inviteCode.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Invite code does not belong to this club");
        }
        inviteCode.setActive(false);
        clubInviteCodeRepository.save(inviteCode);
    }

    private String generateUniqueClubCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (clubInviteCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate unique invite code after 10 attempts");
    }

    // --- Calendar aggregation ---

    public List<CalendarClubSessionResponse> getMyClubSessionsForCalendar(String userId, LocalDate start, LocalDate end) {
        List<ClubMembership> memberships = membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .toList();
        if (memberships.isEmpty()) return List.of();

        List<String> clubIds = memberships.stream().map(ClubMembership::getClubId).toList();
        Map<String, Club> clubMap = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, c -> c));

        List<ClubTrainingSession> sessions = sessionRepository.findByClubIdInAndScheduledAtBetween(
                clubIds, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        // Pre-fetch groups the user belongs to, keyed by clubId
        Set<String> userGroupIds = new HashSet<>();
        for (String clubId : clubIds) {
            clubGroupRepository.findByClubIdAndMemberIdsContaining(clubId, userId)
                    .forEach(g -> userGroupIds.add(g.getId()));
        }

        // Map groupId -> group name
        Map<String, String> groupNameMap = new HashMap<>();
        clubIds.forEach(clubId -> clubGroupRepository.findByClubId(clubId)
                .forEach(g -> groupNameMap.put(g.getId(), g.getName())));

        List<CalendarClubSessionResponse> result = new ArrayList<>();
        for (ClubTrainingSession s : sessions) {
            // If session is scoped to a group, check if user is a member
            // or if the "Open to All" window is active
            if (s.getClubGroupId() != null && !s.getClubGroupId().isBlank()) {
                if (!userGroupIds.contains(s.getClubGroupId())) {
                    LocalDateTime openFrom = s.computeOpenToAllFrom();
                    if (openFrom == null || LocalDateTime.now().isBefore(openFrom)) continue;
                }
            }

            Club club = clubMap.get(s.getClubId());
            if (club == null) continue;

            boolean joined = s.getParticipantIds().contains(userId);
            boolean onWaitingList = s.isOnWaitingList(userId);
            int waitingListPosition = 0;
            if (onWaitingList) {
                for (int i = 0; i < s.getWaitingList().size(); i++) {
                    if (s.getWaitingList().get(i).userId().equals(userId)) {
                        waitingListPosition = i + 1;
                        break;
                    }
                }
            }

            result.add(new CalendarClubSessionResponse(
                    s.getId(), s.getClubId(), club.getName(), s.getTitle(), s.getSport(),
                    s.getScheduledAt(), s.getLocation(), s.getDescription(),
                    s.getDurationMinutes(), s.getParticipantIds(),
                    s.getMaxParticipants(), s.getClubGroupId(),
                    groupNameMap.get(s.getClubGroupId()),
                    joined, onWaitingList, waitingListPosition,
                    s.computeOpenToAllFrom()));
        }
        return result;
    }

    public record CalendarClubSessionResponse(
            String id, String clubId, String clubName, String title, String sport,
            LocalDateTime scheduledAt, String location, String description,
            Integer durationMinutes, List<String> participantIds,
            Integer maxParticipants, String clubGroupId, String clubGroupName,
            boolean joined, boolean onWaitingList, int waitingListPosition,
            LocalDateTime openToAllFrom
    ) {}

    // --- Helpers ---

    private void enrichFromLinkedTraining(ClubTrainingSession session) {
        if (session.getLinkedTrainingId() == null) return;
        try {
            Training t = trainingService.getTrainingById(session.getLinkedTrainingId());
            session.setLinkedTrainingTitle(t.getTitle());
            session.setLinkedTrainingDescription(t.getDescription());
            if (session.getDurationMinutes() == null && t.getEstimatedDurationSeconds() != null) {
                session.setDurationMinutes(t.getEstimatedDurationSeconds() / 60);
            }
        } catch (Exception ignored) {
            // Training may have been deleted
        }
    }

    private void validateAdminRole(String userId, String clubId) {
        ClubMembership m = membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (m.getRole() != ClubMemberRole.OWNER && m.getRole() != ClubMemberRole.ADMIN) {
            throw new IllegalStateException("Admin or owner role required");
        }
    }

    private void validateActiveMember(String userId, String clubId) {
        ClubMembership m = membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (m.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new IllegalStateException("Active membership required");
        }
    }

    private void validateAdminOrOwner(String userId, String clubId) {
        ClubMembership m = membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (m.getRole() != ClubMemberRole.OWNER && m.getRole() != ClubMemberRole.ADMIN) {
            throw new IllegalStateException("Admin or owner role required");
        }
    }

    public void validateAdminOrCoachAccess(String userId, String clubId) {
        validateAdminOrCoach(userId, clubId);
    }

    private void validateAdminOrCoach(String userId, String clubId) {
        ClubMembership m = membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (m.getRole() == ClubMemberRole.MEMBER) {
            throw new IllegalStateException("Coach, admin, or owner role required");
        }
        if (m.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new IllegalStateException("Active membership required");
        }
    }

    private List<String> getActiveMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).collect(Collectors.toList());
    }

    private void emitActivity(String clubId, ClubActivityType type, String actorId,
                               String targetId, String targetTitle) {
        ClubActivity activity = new ClubActivity();
        activity.setClubId(clubId);
        activity.setType(type);
        activity.setActorId(actorId);
        activity.setTargetId(targetId);
        activity.setTargetTitle(targetTitle);
        activity.setOccurredAt(LocalDateTime.now());
        activityRepository.save(activity);

        if (type == ClubActivityType.SESSION_CREATED) {
            List<String> memberIds = getActiveMemberIds(clubId);
            memberIds.remove(actorId);
            if (!memberIds.isEmpty()) {
                User actor = userService.findById(actorId).orElse(null);
                String actorName = actor != null ? actor.getDisplayName() : "Someone";
                notificationService.sendToUsers(
                        memberIds,
                        "New Group Session",
                        actorName + " created a training session: " + targetTitle,
                        Map.of("type", "SESSION_CREATED",
                               "clubId", clubId,
                               "sessionId", targetId != null ? targetId : ""));
            }
        }
    }
}
