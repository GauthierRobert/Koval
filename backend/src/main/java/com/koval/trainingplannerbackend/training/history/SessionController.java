package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    private final CompletedSessionRepository repository;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final GridFsOperations gridFsOperations;

    public SessionController(CompletedSessionRepository repository,
                             AnalyticsService analyticsService,
                             UserRepository userRepository,
                             GridFsOperations gridFsOperations) {
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.gridFsOperations = gridFsOperations;
    }

    @PostMapping
    public ResponseEntity<CompletedSession> save(@RequestBody CompletedSession session) {
        String userId = SecurityUtils.getCurrentUserId();
        session.setUserId(userId);
        if (session.getCompletedAt() == null) {
            session.setCompletedAt(LocalDateTime.now());
        }

        // Compute TSS / IF before saving
        userRepository.findById(userId).ifPresent(user -> {
            int ftp = user.getFtp() != null ? user.getFtp() : 250;
            analyticsService.computeAndAttachMetrics(session, ftp);
        });

        CompletedSession saved = repository.save(session);

        // Update CTL/ATL/TSB on the user document
        analyticsService.recomputeAndSaveUserLoad(userId);

        return ResponseEntity.ok(saved);
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
                    // Delete associated FIT file if present
                    if (s.getFitFileId() != null) {
                        try {
                            gridFsOperations.delete(
                                    org.springframework.data.mongodb.core.query.Query.query(
                                            org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new ObjectId(s.getFitFileId()))
                                    )
                            );
                        } catch (Exception ignored) {}
                    }
                    repository.delete(s);
                    return ResponseEntity.<Void>noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── PMC endpoint ─────────────────────────────────────────────────────────

    @GetMapping("/pmc")
    public ResponseEntity<List<AnalyticsService.PmcDataPoint>> getPmc(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(analyticsService.generatePmc(userId, from, to));
    }

    // ── FIT file upload / download ────────────────────────────────────────────

    @PostMapping("/{id}/fit")
    public ResponseEntity<CompletedSession> uploadFit(@PathVariable String id,
                                                       @RequestParam("file") MultipartFile file) throws IOException {
        String userId = SecurityUtils.getCurrentUserId();
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .map(s -> {
                    try {
                        // Delete old FIT file if replacing
                        if (s.getFitFileId() != null) {
                            try {
                                gridFsOperations.delete(
                                        org.springframework.data.mongodb.core.query.Query.query(
                                                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new ObjectId(s.getFitFileId()))
                                        )
                                );
                            } catch (Exception ignored) {}
                        }
                        ObjectId fileId = gridFsOperations.store(
                                file.getInputStream(),
                                s.getId() + ".fit",
                                "application/octet-stream"
                        );
                        s.setFitFileId(fileId.toHexString());
                        return ResponseEntity.ok(repository.save(s));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/fit")
    public ResponseEntity<?> downloadFit(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .filter(s -> s.getFitFileId() != null)
                .map(s -> {
                    try {
                        com.mongodb.client.gridfs.model.GridFSFile gridFile =
                                gridFsOperations.findOne(
                                        org.springframework.data.mongodb.core.query.Query.query(
                                                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new ObjectId(s.getFitFileId()))
                                        )
                                );
                        if (gridFile == null) return ResponseEntity.<byte[]>notFound().build();

                        org.springframework.data.mongodb.gridfs.GridFsResource resource = gridFsOperations.getResource(gridFile);
                        byte[] bytes = resource.getInputStream().readAllBytes();
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + s.getId() + ".fit\"")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .body(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
