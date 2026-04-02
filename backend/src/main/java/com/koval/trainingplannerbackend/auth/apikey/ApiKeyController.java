package com.koval.trainingplannerbackend.auth.apikey;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(@Valid @RequestBody CreateApiKeyRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        ApiKeyResponse response = apiKeyService.generateKey(userId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyListItem>> listKeys() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(apiKeyService.listKeys(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        apiKeyService.revokeKey(id, userId);
        return ResponseEntity.noContent().build();
    }

    public record CreateApiKeyRequest(@NotBlank String name) {}
}
