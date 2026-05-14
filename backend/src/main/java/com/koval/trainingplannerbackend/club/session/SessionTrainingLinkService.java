package com.koval.trainingplannerbackend.club.session;

import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionMaterializer;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class SessionTrainingLinkService {

    private static final Logger log = LoggerFactory.getLogger(SessionTrainingLinkService.class);

    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubAuthorizationService authorizationService;
    private final TrainingService trainingService;
    private final RecurringSessionMaterializer materializer;

    public SessionTrainingLinkService(ClubTrainingSessionRepository sessionRepository,
                                      ClubGroupRepository clubGroupRepository,
                                      ClubAuthorizationService authorizationService,
                                      TrainingService trainingService,
                                      RecurringSessionMaterializer materializer) {
        this.sessionRepository = sessionRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.authorizationService = authorizationService;
        this.trainingService = trainingService;
        this.materializer = materializer;
    }

    public ClubTrainingSession linkTrainingToSession(String userId, String clubId, String sessionId,
                                                       String trainingId, String clubGroupId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubTrainingSession session = materializer.resolveOrMaterialize(sessionId);
        if (Boolean.TRUE.equals(session.getCancelled())) {
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
        ClubTrainingSession session = materializer.resolveOrMaterialize(sessionId);
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        if (Boolean.TRUE.equals(session.getCancelled())) {
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
        }
    }

    private void enrichGroupLinkedTraining(GroupLinkedTraining glt) {
        if (glt.getTrainingId() == null) return;
        try {
            Training t = trainingService.getTrainingById(glt.getTrainingId());
            glt.setTrainingTitle(t.getTitle());
            glt.setTrainingDescription(t.getDescription());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to enrich group linked training {}: {}", glt.getTrainingId(), e.getMessage());
        }
    }

    GroupLinkedTraining resolveUserLinkedTraining(ClubTrainingSession session, Set<String> userGroupIds) {
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
        return effective.getFirst();
    }
}
