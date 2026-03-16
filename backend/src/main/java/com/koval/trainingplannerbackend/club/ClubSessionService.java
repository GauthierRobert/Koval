package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.club.dto.CalendarClubSessionResponse;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public ClubTrainingSession joinSession(String userId, String sessionId) {
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
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
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubTrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        session.setLinkedTrainingId(trainingId);
        enrichFromLinkedTraining(session);
        trainingService.addClubIdToTraining(trainingId, clubId);
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

        Set<String> userGroupIds = new HashSet<>();
        for (String clubId : clubIds) {
            clubGroupRepository.findByClubIdAndMemberIdsContaining(clubId, userId)
                    .forEach(g -> userGroupIds.add(g.getId()));
        }

        Map<String, String> groupNameMap = new HashMap<>();
        clubIds.forEach(clubId -> clubGroupRepository.findByClubId(clubId)
                .forEach(g -> groupNameMap.put(g.getId(), g.getName())));

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
