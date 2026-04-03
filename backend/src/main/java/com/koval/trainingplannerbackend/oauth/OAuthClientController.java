package com.koval.trainingplannerbackend.oauth;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/oauth/clients")
public class OAuthClientController {

    private final OAuthService oAuthService;

    public OAuthClientController(OAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    @GetMapping
    public List<OAuthService.ClientSummary> listClients() {
        String userId = SecurityUtils.getCurrentUserId();
        return oAuthService.listClients(userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        oAuthService.deleteClient(id, userId);
        return ResponseEntity.noContent().build();
    }
}
