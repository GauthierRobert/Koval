package com.koval.trainingplannerbackend.training.history.compare;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only endpoints powering the session-comparison feature. */
@RestController
@RequestMapping("/api/sessions")
public class SessionComparisonController {

    private final SessionComparisonService comparisonService;

    public SessionComparisonController(SessionComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /** Same-sport sessions ranked by similarity to {@code id}, seed excluded. */
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<SimilarSessionDto>> similar(
            @PathVariable String id,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(comparisonService.findSimilar(userId, id, limit));
    }

    /** Build the N-way comparison report. First id is the reference. */
    @PostMapping("/compare")
    public ResponseEntity<ComparisonReport> compare(@RequestBody CompareRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(comparisonService.compare(userId, request.sessionIds()));
    }

    public record CompareRequest(List<String> sessionIds) {}
}
