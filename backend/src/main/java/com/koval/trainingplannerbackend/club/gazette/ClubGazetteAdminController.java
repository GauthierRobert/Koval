package com.koval.trainingplannerbackend.club.gazette;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazetteEditionResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazettePayloadResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.PublishGazetteRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin/MCP-only endpoints for the gazette: rich curation payload + publish.
 * Both routes require owner/admin/coach role; enforcement happens in the
 * service layer (Phase 4 / Phase 5).
 */
@RestController
@RequestMapping("/api/clubs/{clubId}/gazette")
public class ClubGazetteAdminController {

    private final ClubGazetteService gazetteService;
    private final ClubGazettePublisher publisher;

    public ClubGazetteAdminController(ClubGazetteService gazetteService,
                                      ClubGazettePublisher publisher) {
        this.gazetteService = gazetteService;
        this.publisher = publisher;
    }

    @GetMapping("/editions/{editionId}/payload")
    public ResponseEntity<ClubGazettePayloadResponse> getPayload(
            @PathVariable String clubId,
            @PathVariable String editionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.getPayload(userId, editionId));
    }

    @PostMapping("/editions/{editionId}/publish")
    public ResponseEntity<ClubGazetteEditionResponse> publish(
            @PathVariable String clubId,
            @PathVariable String editionId,
            @RequestBody PublishGazetteRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(publisher.publish(userId, editionId, req));
    }
}
