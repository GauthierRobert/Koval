package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CoachNoteService {

    private static final int MAX_BODY_LENGTH = 10_000;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final CoachNoteRepository repository;
    private final CoachService coachService;

    public CoachNoteService(CoachNoteRepository repository, CoachService coachService) {
        this.repository = repository;
        this.coachService = coachService;
    }

    public CoachNote append(String coachId, String athleteId, String sessionId, String body,
                            Provenance provenance) {
        if (athleteId == null || athleteId.isBlank()) {
            throw new ValidationException("athleteId is required");
        }
        if (body == null || body.isBlank()) {
            throw new ValidationException("body is required");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new ValidationException("body too long (max " + MAX_BODY_LENGTH + " chars)");
        }
        if (!coachService.isCoachOfAthlete(coachId, athleteId)) {
            throw new ForbiddenOperationException("Not authorized: you are not the coach of this athlete");
        }

        CoachNote n = new CoachNote();
        n.setCoachId(coachId);
        n.setAthleteId(athleteId);
        n.setSessionId(sessionId != null && !sessionId.isBlank() ? sessionId : null);
        n.setBody(body);
        n.setProvenance(provenance != null ? provenance : Provenance.web());
        LocalDateTime now = LocalDateTime.now();
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        return repository.save(n);
    }

    public List<CoachNote> listForAthlete(String requesterId, String athleteId, String sessionId,
                                          Integer limit) {
        if (!requesterId.equals(athleteId) && !coachService.isCoachOfAthlete(requesterId, athleteId)) {
            throw new ForbiddenOperationException("Not authorized to view notes for this athlete");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return repository.findByAthleteIdAndSessionIdOrderByCreatedAtDesc(athleteId, sessionId);
        }
        int effective = (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        Pageable page = PageRequest.of(0, effective);
        return repository.findByAthleteIdOrderByCreatedAtDesc(athleteId, page);
    }

    public void delete(String requesterId, String noteId) {
        CoachNote existing = repository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("CoachNote", noteId));
        if (!requesterId.equals(existing.getCoachId())) {
            throw new ForbiddenOperationException("Not authorized to delete this note");
        }
        repository.deleteById(noteId);
    }
}
