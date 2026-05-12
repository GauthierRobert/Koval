package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock
    private AiAnalysisRepository repository;
    @Mock
    private CompletedSessionRepository sessionRepository;
    @Mock
    private CoachService coachService;

    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisService(repository, sessionRepository, coachService);
    }

    private CompletedSession sessionOwnedBy(String ownerId) {
        CompletedSession s = new CompletedSession();
        s.setId("session-1");
        s.setUserId(ownerId);
        return s;
    }

    @Test
    void publish_asSessionOwner_persistsAnalysisWithProvenance() {
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(sessionOwnedBy("athlete-1")));
        when(repository.save(any(AiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAnalysis result = service.publish("athlete-1", "session-1", "ok ride",
                "## Markdown body", List.of("hit FTP", "stayed in zone"), Provenance.mcp());

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());
        AiAnalysis saved = captor.getValue();
        assertEquals("session-1", saved.getSessionId());
        assertEquals("athlete-1", saved.getAthleteId());
        assertEquals("athlete-1", saved.getAuthorId());
        assertEquals("ok ride", saved.getSummary());
        assertEquals("## Markdown body", saved.getBody());
        assertEquals(2, saved.getHighlights().size());
        assertEquals("mcp", saved.getProvenance().source());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertSame(saved, result);
    }

    @Test
    void publish_asCoachOfAthlete_succeeds() {
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(sessionOwnedBy("athlete-1")));
        when(coachService.isCoachOfAthlete("coach-1", "athlete-1")).thenReturn(true);
        when(repository.save(any(AiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        service.publish("coach-1", "session-1", "summary", "body", null, Provenance.mcp());

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());
        assertEquals("coach-1", captor.getValue().getAuthorId());
        assertEquals("athlete-1", captor.getValue().getAthleteId());
    }

    @Test
    void publish_asUnrelatedUser_throwsForbidden() {
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(sessionOwnedBy("athlete-1")));
        when(coachService.isCoachOfAthlete("stranger", "athlete-1")).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () ->
                service.publish("stranger", "session-1", "s", "b", null, Provenance.mcp()));
        verify(repository, never()).save(any());
    }

    @Test
    void publish_missingSession_throwsNotFound() {
        when(sessionRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.publish("athlete-1", "ghost", "s", "b", null, Provenance.mcp()));
    }

    @Test
    void publish_blankSummary_throwsValidation() {
        assertThrows(ValidationException.class, () ->
                service.publish("athlete-1", "session-1", "", "body", null, Provenance.mcp()));
    }

    @Test
    void publish_blankBody_throwsValidation() {
        assertThrows(ValidationException.class, () ->
                service.publish("athlete-1", "session-1", "summary", "", null, Provenance.mcp()));
    }

    @Test
    void publish_bodyTooLong_throwsValidation() {
        String tooLong = "x".repeat(20_001);
        assertThrows(ValidationException.class, () ->
                service.publish("athlete-1", "session-1", "summary", tooLong, null, Provenance.mcp()));
    }

    @Test
    void listForSession_asOwner_returnsAnalyses() {
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(sessionOwnedBy("athlete-1")));
        AiAnalysis a = new AiAnalysis();
        when(repository.findBySessionIdOrderByCreatedAtDesc("session-1")).thenReturn(List.of(a));

        List<AiAnalysis> result = service.listForSession("athlete-1", "session-1");
        assertEquals(1, result.size());
    }

    @Test
    void listForSession_asUnrelated_throwsForbidden() {
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(sessionOwnedBy("athlete-1")));
        when(coachService.isCoachOfAthlete("stranger", "athlete-1")).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () ->
                service.listForSession("stranger", "session-1"));
    }

    @Test
    void delete_byAuthor_succeeds() {
        AiAnalysis existing = new AiAnalysis();
        existing.setAuthorId("coach-1");
        existing.setAthleteId("athlete-1");
        when(repository.findById("a1")).thenReturn(Optional.of(existing));

        service.delete("coach-1", "a1");
        verify(repository).deleteById("a1");
    }

    @Test
    void delete_bySessionOwner_succeeds() {
        AiAnalysis existing = new AiAnalysis();
        existing.setAuthorId("coach-1");
        existing.setAthleteId("athlete-1");
        when(repository.findById("a1")).thenReturn(Optional.of(existing));

        service.delete("athlete-1", "a1");
        verify(repository).deleteById("a1");
    }

    @Test
    void delete_byUnrelated_throwsForbidden() {
        AiAnalysis existing = new AiAnalysis();
        existing.setAuthorId("coach-1");
        existing.setAthleteId("athlete-1");
        when(repository.findById("a1")).thenReturn(Optional.of(existing));

        assertThrows(ForbiddenOperationException.class, () -> service.delete("stranger", "a1"));
        verify(repository, never()).deleteById(any());
    }
}
