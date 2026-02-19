package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    private final CompletedSessionRepository repository;

    public SessionController(CompletedSessionRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<CompletedSession> save(@RequestBody CompletedSession session) {
        String userId = SecurityUtils.getCurrentUserId();
        session.setUserId(userId);
        if (session.getCompletedAt() == null) {
            session.setCompletedAt(LocalDateTime.now());
        }
        return ResponseEntity.ok(repository.save(session));
    }

    @GetMapping
    public ResponseEntity<List<CompletedSession>> list() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(repository.findByUserIdOrderByCompletedAtDesc(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .map(s -> {
                    repository.delete(s);
                    return ResponseEntity.<Void>noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
