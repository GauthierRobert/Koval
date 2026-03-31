package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.CancelSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
public class ClubRecurringSessionController {

    private final RecurringSessionService recurringSessionService;

    public ClubRecurringSessionController(RecurringSessionService recurringSessionService) {
        this.recurringSessionService = recurringSessionService;
    }

    @PostMapping("/{id}/recurring-sessions")
    public ResponseEntity<RecurringSessionTemplate> createRecurringSession(
            @PathVariable String id, @RequestBody CreateRecurringSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringSessionService.createTemplate(userId, id, req));
    }

    @GetMapping("/{id}/recurring-sessions")
    public ResponseEntity<List<RecurringSessionTemplate>> listRecurringSessions(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringSessionService.listTemplates(userId, id));
    }

    @PutMapping("/{id}/recurring-sessions/{templateId}")
    public ResponseEntity<RecurringSessionTemplate> updateRecurringSession(
            @PathVariable String id, @PathVariable String templateId,
            @RequestBody CreateRecurringSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringSessionService.updateTemplate(userId, templateId, req));
    }

    @PutMapping("/{id}/recurring-sessions/{templateId}/with-instances")
    public ResponseEntity<RecurringSessionTemplate> updateRecurringSessionWithInstances(
            @PathVariable String id, @PathVariable String templateId,
            @RequestBody CreateRecurringSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        RecurringSessionTemplate template = recurringSessionService.updateTemplate(userId, templateId, req);
        recurringSessionService.updateFutureInstances(templateId);
        return ResponseEntity.ok(template);
    }

    @PutMapping("/{id}/recurring-sessions/{templateId}/cancel-future")
    public ResponseEntity<Map<String, Integer>> cancelFutureRecurringSessions(
            @PathVariable String id, @PathVariable String templateId,
            @RequestBody CancelSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        int cancelledCount = recurringSessionService.cancelFutureInstances(userId, id, templateId, req.reason());
        return ResponseEntity.ok(Map.of("cancelledCount", cancelledCount));
    }

    @DeleteMapping("/{id}/recurring-sessions/{templateId}")
    public ResponseEntity<Void> deactivateRecurringSession(
            @PathVariable String id, @PathVariable String templateId) {
        String userId = SecurityUtils.getCurrentUserId();
        recurringSessionService.deactivateTemplate(userId, templateId);
        return ResponseEntity.noContent().build();
    }
}
