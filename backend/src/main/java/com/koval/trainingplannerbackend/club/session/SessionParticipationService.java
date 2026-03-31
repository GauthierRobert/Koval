package com.koval.trainingplannerbackend.club.session;

import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.activity.ClubActivityType;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class SessionParticipationService {

    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubRepository clubRepository;
    private final NotificationService notificationService;
    private final ClubActivityService activityService;

    public SessionParticipationService(ClubTrainingSessionRepository sessionRepository,
                                       ClubGroupRepository clubGroupRepository,
                                       ClubRepository clubRepository,
                                       NotificationService notificationService,
                                       ClubActivityService activityService) {
        this.sessionRepository = sessionRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.clubRepository = clubRepository;
        this.notificationService = notificationService;
        this.activityService = activityService;
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

    static int computeWaitingListPosition(ClubTrainingSession session, String userId) {
        for (int i = 0; i < session.getWaitingList().size(); i++) {
            if (session.getWaitingList().get(i).userId().equals(userId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static final DateTimeFormatter SESSION_DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm");

    private String formatSessionDate(ClubTrainingSession session) {
        return session.getScheduledAt() != null ? session.getScheduledAt().format(SESSION_DATE_FMT) : "";
    }

    private String getClubName(String clubId) {
        return clubRepository.findById(clubId).map(Club::getName).orElse("Club");
    }
}
