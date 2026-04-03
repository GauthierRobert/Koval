package com.koval.trainingplannerbackend.club.session;

import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.activity.ClubActivityType;
import com.koval.trainingplannerbackend.club.dto.CalendarClubSessionResponse;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClubSessionService {

    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubAuthorizationService authorizationService;
    private final SessionTrainingLinkService trainingLinkService;
    private final NotificationService notificationService;
    private final ClubActivityService activityService;

    public ClubSessionService(ClubTrainingSessionRepository sessionRepository,
                              ClubMembershipRepository membershipRepository,
                              ClubRepository clubRepository,
                              ClubGroupRepository clubGroupRepository,
                              ClubAuthorizationService authorizationService,
                              SessionTrainingLinkService trainingLinkService,
                              NotificationService notificationService,
                              ClubActivityService activityService) {
        this.sessionRepository = sessionRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.authorizationService = authorizationService;
        this.trainingLinkService = trainingLinkService;
        this.notificationService = notificationService;
        this.activityService = activityService;
    }

    public ClubTrainingSession createSession(String userId, String clubId, CreateSessionRequest req) {
        SessionCategory cat = req.category() != null ? req.category() : SessionCategory.SCHEDULED;
        if (cat == SessionCategory.OPEN) {
            authorizationService.requireActiveMember(userId, clubId);
        } else {
            authorizationService.requireAdminOrCoach(userId, clubId);
        }

        ClubTrainingSession session = new ClubTrainingSession();
        session.setClubId(clubId);
        session.setCreatedBy(userId);
        session.setCreatedAt(LocalDateTime.now());
        SessionPropertyMapper.applyRequest(req, session);
        trainingLinkService.enrichFromLinkedTraining(session);
        session = sessionRepository.save(session);

        activityService.emitActivity(clubId, ClubActivityType.SESSION_CREATED, userId, session.getId(), session.getTitle());

        String prefType = (cat == SessionCategory.OPEN) ? "openSessionCreated" : "clubSessionCreated";
        String notifPrefix = (cat == SessionCategory.OPEN) ? "Open Session" : "New Session";
        notifyClubMembers(clubId, session,
                getClubName(clubId) + " — " + notifPrefix,
                "\"" + session.getTitle() + "\" — " + formatSessionDate(session),
                "SESSION_CREATED", prefType);

        return session;
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId) {
        return listSessions(userId, clubId, (SessionCategory) null);
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId, SessionCategory category) {
        List<ClubTrainingSession> all = category != null
                ? sessionRepository.findByClubIdAndCategoryOrderByScheduledAtDesc(clubId, category)
                : sessionRepository.findByClubIdOrderByScheduledAtDesc(clubId);
        return filterByGroupVisibility(userId, clubId, all);
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId, LocalDateTime from, LocalDateTime to) {
        return listSessions(userId, clubId, null, from, to);
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId, SessionCategory category,
                                                    LocalDateTime from, LocalDateTime to) {
        List<ClubTrainingSession> all = category != null
                ? sessionRepository.findByClubIdAndCategoryAndScheduledAtBetween(clubId, category, from, to)
                : sessionRepository.findByClubIdAndScheduledAtBetween(clubId, from, to);
        return filterByGroupVisibility(userId, clubId, all);
    }

    public ClubTrainingSession cancelEntireSession(String userId, String clubId, String sessionId, String reason) {
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        authorizeSessionModification(userId, clubId, session);
        if (Boolean.TRUE.equals(session.getCancelled())) {
            throw new IllegalStateException("Session is already cancelled");
        }
        session.setCancelled(true);
        session.setCancellationReason(reason);
        session.setCancelledAt(LocalDateTime.now());
        sessionRepository.save(session);
        activityService.emitActivity(clubId, ClubActivityType.SESSION_CANCELLED, userId, sessionId, session.getTitle());

        // Notify participants about cancellation
        if (!session.getParticipantIds().isEmpty()) {
            String body = getClubName(clubId) + " — \"" + session.getTitle() + "\" (" + formatSessionDate(session) + ") has been cancelled"
                    + (reason != null && !reason.isBlank() ? ": " + reason : "");
            notificationService.sendToUsers(
                    session.getParticipantIds(),
                    "Session Cancelled",
                    body,
                    Map.of("type", "SESSION_CANCELLED",
                           "clubId", clubId,
                           "sessionId", sessionId),
                    "clubSessionCancelled");
        }

        return session;
    }

    public ClubTrainingSession updateSession(String userId, String clubId, String sessionId,
                                              CreateSessionRequest req) {
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        authorizeSessionModification(userId, clubId, session);
        if (Boolean.TRUE.equals(session.getCancelled())) {
            throw new IllegalStateException("Cannot update a cancelled session");
        }
        String previousLinkedTrainingId = session.getLinkedTrainingId();
        SessionPropertyMapper.applyRequest(req, session);
        if (req.linkedTrainingId() != null && !req.linkedTrainingId().equals(previousLinkedTrainingId)) {
            trainingLinkService.enrichFromLinkedTraining(session);
        }
        return sessionRepository.save(session);
    }

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

        // Batch group queries instead of per-club loops
        Set<String> userGroupIds = clubGroupRepository.findByClubIdInAndMemberIdsContaining(clubIds, userId)
                .stream().map(ClubGroup::getId).collect(Collectors.toSet());

        Map<String, String> groupNameMap = clubGroupRepository.findByClubIdIn(clubIds).stream()
                .collect(Collectors.toMap(ClubGroup::getId, ClubGroup::getName));

        List<CalendarClubSessionResponse> result = new ArrayList<>();
        for (ClubTrainingSession s : sessions) {
            if (s.getClubGroupId() != null && !s.getClubGroupId().isBlank()) {
                if (!userGroupIds.contains(s.getClubGroupId())) {
                    LocalDateTime openFrom = s.computeOpenToAllFrom();
                    if (openFrom == null || LocalDateTime.now().isBefore(openFrom)) continue;
                }
            }

            Club club = clubMap.get(s.getClubId());
            if (club == null) continue;

            result.add(toCalendarResponse(s, userId, club, groupNameMap, userGroupIds));
        }
        return result;
    }

    private CalendarClubSessionResponse toCalendarResponse(ClubTrainingSession s, String userId,
                                                            Club club, Map<String, String> groupNameMap,
                                                            Set<String> userGroupIds) {
        boolean joined = s.getParticipantIds().contains(userId);
        boolean onWaitingList = s.isOnWaitingList(userId);
        int waitingListPosition = SessionParticipationService.computeWaitingListPosition(s, userId);

        GroupLinkedTraining resolved = trainingLinkService.resolveUserLinkedTraining(s, userGroupIds);

        List<GroupLinkedTraining> effective = s.getEffectiveLinkedTrainings();
        List<CalendarClubSessionResponse.CalendarLinkedTraining> linkedTrainings = effective.stream()
                .map(glt -> new CalendarClubSessionResponse.CalendarLinkedTraining(
                        glt.getTrainingId(),
                        glt.getTrainingTitle(),
                        glt.getClubGroupId(),
                        glt.getClubGroupId() != null ? groupNameMap.getOrDefault(glt.getClubGroupId(), glt.getClubGroupName()) : null,
                        glt.getClubGroupId() == null || userGroupIds.contains(glt.getClubGroupId())
                )).toList();

        return new CalendarClubSessionResponse(
                s.getId(), s.getClubId(), club.getName(), s.getTitle(), s.getSport(),
                s.getScheduledAt(), s.getLocation(), s.getDescription(),
                s.getDurationMinutes(), s.getParticipantIds(),
                s.getMaxParticipants(), s.getClubGroupId(),
                groupNameMap.get(s.getClubGroupId()),
                joined, onWaitingList, waitingListPosition,
                s.computeOpenToAllFrom(),
                Boolean.TRUE.equals(s.getCancelled()), s.getCancellationReason(),
                resolved != null ? resolved.getTrainingId() : null,
                resolved != null ? resolved.getTrainingTitle() : null,
                resolved != null ? resolved.getTrainingDescription() : null,
                linkedTrainings);
    }

    private List<ClubTrainingSession> filterByGroupVisibility(String userId, String clubId,
                                                               List<ClubTrainingSession> sessions) {
        if (authorizationService.isAdminOrCoach(userId, clubId)) {
            return sessions;
        }
        Set<String> userGroupIds = clubGroupRepository.findByClubIdAndMemberIdsContaining(clubId, userId)
                .stream().map(ClubGroup::getId).collect(Collectors.toSet());
        return sessions.stream().filter(s -> {
            if (s.getClubGroupId() == null || s.getClubGroupId().isBlank()) return true;
            if (userGroupIds.contains(s.getClubGroupId())) return true;
            LocalDateTime openFrom = s.computeOpenToAllFrom();
            return openFrom != null && !LocalDateTime.now().isBefore(openFrom);
        }).toList();
    }

    private void notifyClubMembers(String clubId, ClubTrainingSession session,
                                     String title, String body, String type, String preferenceType) {
        List<String> memberIds = membershipRepository.findByClubId(clubId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .map(ClubMembership::getUserId)
                .filter(id -> !id.equals(session.getCreatedBy()))
                .toList();
        if (!memberIds.isEmpty()) {
            notificationService.sendToUsers(memberIds, title, body,
                    Map.of("type", type,
                           "clubId", clubId,
                           "sessionId", session.getId()),
                    preferenceType);
        }
    }

    private String getClubName(String clubId) {
        return clubRepository.findById(clubId).map(Club::getName).orElse("Club");
    }

    private static final DateTimeFormatter SESSION_DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm");

    private String formatSessionDate(ClubTrainingSession session) {
        return session.getScheduledAt() != null ? session.getScheduledAt().format(SESSION_DATE_FMT) : "";
    }

    private void authorizeSessionModification(String userId, String clubId, ClubTrainingSession session) {
        if (session.getCategory() == SessionCategory.OPEN && session.getCreatedBy().equals(userId)) {
            authorizationService.requireActiveMember(userId, clubId);
        } else {
            authorizationService.requireAdminOrCoach(userId, clubId);
        }
    }
}
