package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.config.Provenance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/coach")
public class CoachNoteController {

    private final CoachNoteService noteService;

    public CoachNoteController(CoachNoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/athletes/{athleteId}/notes")
    public ResponseEntity<List<CoachNoteDto>> listForAthlete(
            @PathVariable String athleteId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Integer limit) {
        String userId = SecurityUtils.getCurrentUserId();
        List<CoachNoteDto> result = noteService.listForAthlete(userId, athleteId, sessionId, limit).stream()
                .map(CoachNoteDto::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        noteService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    public record CoachNoteDto(
            String id,
            String coachId,
            String athleteId,
            String sessionId,
            String body,
            Provenance provenance,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static CoachNoteDto from(CoachNote n) {
            return new CoachNoteDto(
                    n.getId(), n.getCoachId(), n.getAthleteId(), n.getSessionId(),
                    n.getBody(), n.getProvenance(), n.getCreatedAt(), n.getUpdatedAt());
        }
    }
}
