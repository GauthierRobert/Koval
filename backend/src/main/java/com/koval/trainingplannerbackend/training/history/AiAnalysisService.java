package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AiAnalysisService {

    private static final int MAX_BODY_LENGTH = 20_000;
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_HIGHLIGHTS = 10;

    private final AiAnalysisRepository repository;
    private final CompletedSessionRepository sessionRepository;
    private final CoachService coachService;

    public AiAnalysisService(AiAnalysisRepository repository,
                             CompletedSessionRepository sessionRepository,
                             CoachService coachService) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.coachService = coachService;
    }

    public AiAnalysis publish(String authorId, String sessionId, String summary, String body,
                              List<String> highlights, Provenance provenance) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ValidationException("sessionId is required");
        }
        if (summary == null || summary.isBlank()) {
            throw new ValidationException("summary is required");
        }
        if (body == null || body.isBlank()) {
            throw new ValidationException("body is required");
        }
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new ValidationException("summary too long (max " + MAX_SUMMARY_LENGTH + " chars)");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new ValidationException("body too long (max " + MAX_BODY_LENGTH + " chars)");
        }
        if (highlights != null && highlights.size() > MAX_HIGHLIGHTS) {
            throw new ValidationException("too many highlights (max " + MAX_HIGHLIGHTS + ")");
        }

        CompletedSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CompletedSession", sessionId));
        String athleteId = session.getUserId();
        if (!authorId.equals(athleteId) && !coachService.isCoachOfAthlete(authorId, athleteId)) {
            throw new ForbiddenOperationException(
                    "Not authorized to publish analysis on this session");
        }

        AiAnalysis a = new AiAnalysis();
        a.setSessionId(sessionId);
        a.setAthleteId(athleteId);
        a.setAuthorId(authorId);
        a.setSummary(summary);
        a.setBody(body);
        a.setHighlights(highlights);
        a.setProvenance(provenance != null ? provenance : Provenance.web());
        LocalDateTime now = LocalDateTime.now();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        return repository.save(a);
    }

    public List<AiAnalysis> listForSession(String requesterId, String sessionId) {
        CompletedSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CompletedSession", sessionId));
        String athleteId = session.getUserId();
        if (!requesterId.equals(athleteId) && !coachService.isCoachOfAthlete(requesterId, athleteId)) {
            throw new ForbiddenOperationException("Not authorized to view analyses for this session");
        }
        return repository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    public void delete(String requesterId, String analysisId) {
        AiAnalysis existing = repository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("AiAnalysis", analysisId));
        if (!requesterId.equals(existing.getAuthorId())
                && !requesterId.equals(existing.getAthleteId())) {
            throw new ForbiddenOperationException("Not authorized to delete this analysis");
        }
        repository.deleteById(analysisId);
    }
}
