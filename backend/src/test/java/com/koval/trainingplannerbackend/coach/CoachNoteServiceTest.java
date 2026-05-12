package com.koval.trainingplannerbackend.coach;

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
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachNoteServiceTest {

    @Mock
    private CoachNoteRepository repository;
    @Mock
    private CoachService coachService;

    private CoachNoteService service;

    @BeforeEach
    void setUp() {
        service = new CoachNoteService(repository, coachService);
    }

    @Test
    void append_asCoach_persistsNoteWithProvenance() {
        when(coachService.isCoachOfAthlete("coach-1", "athlete-1")).thenReturn(true);
        when(repository.save(any(CoachNote.class))).thenAnswer(inv -> inv.getArgument(0));

        CoachNote result = service.append("coach-1", "athlete-1", "session-1",
                "Great work on the intervals.", Provenance.mcp());

        ArgumentCaptor<CoachNote> captor = ArgumentCaptor.forClass(CoachNote.class);
        verify(repository).save(captor.capture());
        CoachNote saved = captor.getValue();
        assertEquals("coach-1", saved.getCoachId());
        assertEquals("athlete-1", saved.getAthleteId());
        assertEquals("session-1", saved.getSessionId());
        assertEquals("Great work on the intervals.", saved.getBody());
        assertEquals("mcp", saved.getProvenance().source());
        assertNotNull(saved.getCreatedAt());
        assertSame(saved, result);
    }

    @Test
    void append_blankSessionId_storesNull() {
        when(coachService.isCoachOfAthlete("coach-1", "athlete-1")).thenReturn(true);
        when(repository.save(any(CoachNote.class))).thenAnswer(inv -> inv.getArgument(0));

        service.append("coach-1", "athlete-1", "  ", "body", Provenance.mcp());

        ArgumentCaptor<CoachNote> captor = ArgumentCaptor.forClass(CoachNote.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getSessionId());
    }

    @Test
    void append_byNonCoach_throwsForbidden() {
        when(coachService.isCoachOfAthlete("stranger", "athlete-1")).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () ->
                service.append("stranger", "athlete-1", null, "body", Provenance.mcp()));
        verify(repository, never()).save(any());
    }

    @Test
    void append_blankBody_throwsValidation() {
        assertThrows(ValidationException.class, () ->
                service.append("coach-1", "athlete-1", null, "", Provenance.mcp()));
    }

    @Test
    void append_blankAthleteId_throwsValidation() {
        assertThrows(ValidationException.class, () ->
                service.append("coach-1", "", null, "body", Provenance.mcp()));
    }

    @Test
    void append_bodyTooLong_throwsValidation() {
        String tooLong = "x".repeat(10_001);
        assertThrows(ValidationException.class, () ->
                service.append("coach-1", "athlete-1", null, tooLong, Provenance.mcp()));
    }

    @Test
    void listForAthlete_asAthlete_returnsNotes() {
        when(repository.findByAthleteIdOrderByCreatedAtDesc(eq("athlete-1"), any(Pageable.class)))
                .thenReturn(List.of(new CoachNote()));

        List<CoachNote> result = service.listForAthlete("athlete-1", "athlete-1", null, null);
        assertEquals(1, result.size());
    }

    @Test
    void listForAthlete_asCoach_returnsNotes() {
        when(coachService.isCoachOfAthlete("coach-1", "athlete-1")).thenReturn(true);
        when(repository.findByAthleteIdOrderByCreatedAtDesc(eq("athlete-1"), any(Pageable.class)))
                .thenReturn(List.of(new CoachNote()));

        List<CoachNote> result = service.listForAthlete("coach-1", "athlete-1", null, null);
        assertEquals(1, result.size());
    }

    @Test
    void listForAthlete_withSessionFilter_usesSessionQuery() {
        when(repository.findByAthleteIdAndSessionIdOrderByCreatedAtDesc("athlete-1", "session-1"))
                .thenReturn(List.of(new CoachNote(), new CoachNote()));

        List<CoachNote> result = service.listForAthlete("athlete-1", "athlete-1", "session-1", null);
        assertEquals(2, result.size());
        verify(repository, never()).findByAthleteIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listForAthlete_byUnrelated_throwsForbidden() {
        when(coachService.isCoachOfAthlete("stranger", "athlete-1")).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () ->
                service.listForAthlete("stranger", "athlete-1", null, null));
    }

    @Test
    void delete_byAuthor_succeeds() {
        CoachNote existing = new CoachNote();
        existing.setCoachId("coach-1");
        when(repository.findById("n1")).thenReturn(Optional.of(existing));

        service.delete("coach-1", "n1");
        verify(repository).deleteById("n1");
    }

    @Test
    void delete_byNonAuthor_throwsForbidden() {
        CoachNote existing = new CoachNote();
        existing.setCoachId("coach-1");
        when(repository.findById("n1")).thenReturn(Optional.of(existing));

        assertThrows(ForbiddenOperationException.class, () -> service.delete("other-coach", "n1"));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete("coach-1", "ghost"));
    }
}
