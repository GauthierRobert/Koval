package com.koval.trainingplannerbackend.club.session;

import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.activity.ClubActivityType;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.dto.CalendarClubSessionResponse;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClubSessionService {

    private static final Logger log = LoggerFactory.getLogger(ClubSessionService.class);

    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubAuthorizationService authorizationService;
    private final TrainingService trainingService;
    private final NotificationService notificationService;
    private final ClubActivityService activityService;

    public ClubSessionService(ClubTrainingSessionRepository sessionRepository,
                              ClubMembershipRepository membershipRepository,
                              ClubRepository clubRepository,
                              ClubGroupRepository clubGroupRepository,
                              ClubAuthorizationService authorizationService,
                              TrainingService trainingService,
                              NotificationService notificationService,
                              ClubActivityService activityService) {
        this.sessionRepository = sessionRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.authorizationService = authorizationService;
        this.trainingService = trainingService;
        this.notificationService = notificationService;
        this.activityService = activityService;
    }

    public ClubTrainingSession createSession(String userId, String clubId, CreateSessionRequest req) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        ClubTrainingSession session = new ClubTrainingSession();
        session.setClubId(clubId);
        session.setCreatedBy(userId);
        session.setCreatedAt(LocalDateTime.now());
        SessionPropertyMapper.applyRequest(req, session);
        enrichFromLinkedTraining(session);
        session = sessionRepository.save(session);

        activityService.emitActivity(clubId, ClubActivityType.SESSION_CREATED, userId, session.getId(), session.getTitle());

        // Notify active club members about the new session
        notifyClubMembers(clubId, session,
                getClubName(clubId) + " — New Session",
                "\"" + session.getTitle() + "\" — " + formatSessionDate(session),
                "SESSION_CREATED", "clubSessionCreated");

        return session;
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId) {
        List<ClubTrainingSession> all = sessionRepository.findByClubIdOrderByScheduledAtDesc(clubId);
        return filterByGroupVisibility(userId, clubId, all);
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId, LocalDateTime from, LocalDateTime to) {
        List<ClubTrainingSession> all = sessionRepository.findByClubIdAndScheduledAtBetween(clubId, from, to);
        return filterByGroupVisibility(userId, clubId, all);
    }

    public ClubTrainingSession cancelEntireSession(String userId, String clubId, String sessionId, String reason) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        if (session.isCancelled()) {
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

    public ClubTrainingSession joinSession(String userId, String sessionId) {
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (session.isCancelled()) {
            throw new IllegalStateException("Cannot join a cancelled session");
        }
        if (session.getClubGroupId() != null && !session.getClubGroupId().isBlank()) {
            boolean inGroup = clubGroupRepository.findByClubIdAndMemberIdsContaining(session.getClubId(), userId)
                    .stream().anyMatch(g -> g.getId().equals(session.getClubGroupId()));
            if (!inGroup) {
                LocalDateTime openFrom = session.computeOpenToAllFrom();
                if (openFrom == null || LocalDateTime.now().isBefore(openFrom)) {
                    throw new IllegalStateException("This session is restricted to group members");
                }
            }
        }
        if (session.getParticipantIds().contains(userId)) {
            return session;
        }
        if (session.isOnWaitingList(userId)) {
            return session;
        }
        if (!session.isFull()) {
            session.getParticipantIds().add(userId);
            sessionRepository.save(session);
            activityService.emitActivity(session.getClubId(), ClubActivityType.SESSION_JOINED, userId, sessionId, session.getTitle());
        } else {
            session.getWaitingList().add(new WaitingListEntry(userId, LocalDateTime.now()));
            sessionRepository.save(session);
            activityService.emitActivity(session.getClubId(), ClubActivityType.WAITING_LIST_JOINED, userId, sessionId, session.getTitle());
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
        WaitingListEntry promoted = session.getWaitingList().removeFirst();
        session.getParticipantIds().add(promoted.userId());

        notificationService.sendToUsers(
                List.of(promoted.userId()),
                "You're In!",
                getClubName(session.getClubId()) + " — A spot opened in \"" + session.getTitle() + "\" (" + formatSessionDate(session) + "). You've been automatically added.",
                Map.of("type", "WAITING_LIST_PROMOTED",
                       "clubId", session.getClubId(),
                       "sessionId", session.getId()),
                "waitingListPromoted");
    }

    public ClubTrainingSession linkTrainingToSession(String userId, String clubId, String sessionId,
                                                       String trainingId, String clubGroupId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (session.isCancelled()) {
            throw new IllegalStateException("Cannot link training to a cancelled session");
        }

        GroupLinkedTraining glt = new GroupLinkedTraining();
        glt.setClubGroupId(clubGroupId);
        glt.setTrainingId(trainingId);
        if (clubGroupId != null) {
            clubGroupRepository.findById(clubGroupId)
                    .ifPresent(g -> glt.setClubGroupName(g.getName()));
        }
        enrichGroupLinkedTraining(glt);

        // Add or replace entry for this group
        List<GroupLinkedTraining> list = session.getLinkedTrainings();
        if (list == null) {
            list = new ArrayList<>();
            session.setLinkedTrainings(list);
        }
        list.removeIf(existing -> Objects.equals(existing.getClubGroupId(), clubGroupId));
        list.add(glt);

        // Also set legacy field for backward compat when clubGroupId is null
        if (clubGroupId == null) {
            session.setLinkedTrainingId(trainingId);
            enrichFromLinkedTraining(session);
        }

        trainingService.addClubIdToTraining(trainingId, clubId);
        return sessionRepository.save(session);
    }

    public ClubTrainingSession unlinkTrainingFromSession(String userId, String clubId, String sessionId,
                                                           String clubGroupId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        if (session.isCancelled()) {
            throw new IllegalStateException("Cannot unlink training from a cancelled session");
        }

        List<GroupLinkedTraining> list = session.getLinkedTrainings();
        if (list != null) {
            list.removeIf(existing -> Objects.equals(clubGroupId, existing.getClubGroupId()));
        }

        // Also clear legacy fields when unlinking club-level entry
        if (clubGroupId == null) {
            session.setLinkedTrainingId(null);
            session.setLinkedTrainingTitle(null);
            session.setLinkedTrainingDescription(null);
        }

        return sessionRepository.save(session);
    }

    public ClubTrainingSession updateSession(String userId, String clubId, String sessionId,
                                              CreateSessionRequest req) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        if (session.isCancelled()) {
            throw new IllegalStateException("Cannot update a cancelled session");
        }
        String previousLinkedTrainingId = session.getLinkedTrainingId();
        SessionPropertyMapper.applyRequest(req, session);
        if (req.linkedTrainingId() != null && !req.linkedTrainingId().equals(previousLinkedTrainingId)) {
            enrichFromLinkedTraining(session);
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
        int waitingListPosition = computeWaitingListPosition(s, userId);

        GroupLinkedTraining resolved = resolveUserLinkedTraining(s, userGroupIds);

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
                s.isCancelled(), s.getCancellationReason(),
                resolved != null ? resolved.getTrainingId() : null,
                resolved != null ? resolved.getTrainingTitle() : null,
                resolved != null ? resolved.getTrainingDescription() : null,
                linkedTrainings);
    }

    private static int computeWaitingListPosition(ClubTrainingSession session, String userId) {
        for (int i = 0; i < session.getWaitingList().size(); i++) {
            if (session.getWaitingList().get(i).userId().equals(userId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private GroupLinkedTraining resolveUserLinkedTraining(ClubTrainingSession session, Set<String> userGroupIds) {
        List<GroupLinkedTraining> effective = session.getEffectiveLinkedTrainings();
        if (effective.isEmpty()) return null;
        // First: find entry matching user's group
        for (GroupLinkedTraining glt : effective) {
            if (glt.getClubGroupId() != null && userGroupIds.contains(glt.getClubGroupId())) {
                return glt;
            }
        }
        // Fall back: club-level entry (null clubGroupId)
        for (GroupLinkedTraining glt : effective) {
            if (glt.getClubGroupId() == null) {
                return glt;
            }
        }
        // Last resort: first entry
        return effective.get(0);
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

    private void enrichGroupLinkedTraining(GroupLinkedTraining glt) {
        if (glt.getTrainingId() == null) return;
        try {
            Training t = trainingService.getTrainingById(glt.getTrainingId());
            glt.setTrainingTitle(t.getTitle());
            glt.setTrainingDescription(t.getDescription());
        } catch (Exception e) {
            log.warn("Failed to enrich group linked training {}: {}", glt.getTrainingId(), e.getMessage());
        }
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

    void enrichFromLinkedTraining(ClubTrainingSession session) {
        if (session.getLinkedTrainingId() == null) return;
        try {
            Training t = trainingService.getTrainingById(session.getLinkedTrainingId());
            session.setLinkedTrainingTitle(t.getTitle());
            session.setLinkedTrainingDescription(t.getDescription());
            if (session.getDurationMinutes() == null && t.getEstimatedDurationSeconds() != null) {
                session.setDurationMinutes(t.getEstimatedDurationSeconds() / 60);
            }
        } catch (IllegalArgumentException e) {
            // Training may have been deleted
        } catch (Exception e) {
            log.warn("Failed to enrich session from linked training {}: {}", session.getLinkedTrainingId(), e.getMessage());
        }
    }
}
