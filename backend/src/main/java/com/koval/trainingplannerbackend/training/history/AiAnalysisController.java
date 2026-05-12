package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.config.Provenance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AiAnalysisController {

    private final AiAnalysisService analysisService;

    public AiAnalysisController(AiAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/sessions/{sessionId}/analyses")
    public ResponseEntity<List<AiAnalysisDto>> listForSession(@PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        List<AiAnalysisDto> result = analysisService.listForSession(userId, sessionId).stream()
                .map(AiAnalysisDto::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/analyses/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        analysisService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    public record AiAnalysisDto(
            String id,
            String sessionId,
            String athleteId,
            String authorId,
            String summary,
            String body,
            List<String> highlights,
            Provenance provenance,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static AiAnalysisDto from(AiAnalysis a) {
            return new AiAnalysisDto(
                    a.getId(), a.getSessionId(), a.getAthleteId(), a.getAuthorId(),
                    a.getSummary(), a.getBody(), a.getHighlights(),
                    a.getProvenance(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }
}
