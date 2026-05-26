package com.koval.trainingplannerbackend.context;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    @Mock
    private AthleteContextRepository athleteRepo;
    @Mock
    private CoachContextRepository coachRepo;
    @Mock
    private CoachService coachService;
    @Mock
    private UserService userService;

    private ContextService service;

    @BeforeEach
    void setUp() {
        service = new ContextService(athleteRepo, coachRepo, coachService, userService);
    }

    private void mockUser(String id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        when(userService.getUserById(id)).thenReturn(u);
    }

    private static Map<String, String> sections() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Weekly availability", "8-10h/week, rest Mondays");
        return m;
    }

    @Test
    void getMyContext_asAthlete_readsSelfEntry() {
        mockUser("ath-1", UserRole.ATHLETE);
        AthleteContext self = new AthleteContext();
        self.setSections(sections());
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "ath-1")).thenReturn(Optional.of(self));

        ContextService.MyContext result = service.getMyContext("ath-1");

        assertEquals(UserRole.ATHLETE.name(), result.role());
        assertEquals(sections(), result.sections());
        verify(coachRepo, never()).findByCoachId(any());
    }

    @Test
    void getMyContext_asCoach_readsPhilosophy() {
        mockUser("coach-1", UserRole.COACH);
        CoachContext phil = new CoachContext();
        phil.setSections(sections());
        when(coachRepo.findByCoachId("coach-1")).thenReturn(Optional.of(phil));

        ContextService.MyContext result = service.getMyContext("coach-1");

        assertEquals(UserRole.COACH.name(), result.role());
        assertEquals(sections(), result.sections());
        verify(athleteRepo, never()).findByAthleteIdAndAuthorId(any(), any());
    }

    @Test
    void getMyContext_whenNothingStored_returnsEmptyWithRole() {
        mockUser("ath-1", UserRole.ATHLETE);
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "ath-1")).thenReturn(Optional.empty());

        ContextService.MyContext result = service.getMyContext("ath-1");

        assertEquals(UserRole.ATHLETE.name(), result.role());
        assertNull(result.sections());
    }

    @Test
    void upsertMyContext_asAthlete_writesSelfAuthoredEntry() {
        mockUser("ath-1", UserRole.ATHLETE);
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "ath-1")).thenReturn(Optional.empty());
        when(athleteRepo.save(any(AthleteContext.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsertMyContext("ath-1", sections(), Provenance.mcp());

        ArgumentCaptor<AthleteContext> captor = ArgumentCaptor.forClass(AthleteContext.class);
        verify(athleteRepo).save(captor.capture());
        AthleteContext saved = captor.getValue();
        assertEquals("ath-1", saved.getAthleteId());
        assertEquals("ath-1", saved.getAuthorId());
        assertEquals(ContextAuthorRole.ATHLETE, saved.getAuthorRole());
        assertEquals("mcp", saved.getProvenance().source());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void upsertMyContext_asCoach_writesPhilosophy() {
        mockUser("coach-1", UserRole.COACH);
        when(coachRepo.findByCoachId("coach-1")).thenReturn(Optional.empty());
        when(coachRepo.save(any(CoachContext.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsertMyContext("coach-1", sections(), Provenance.web());

        ArgumentCaptor<CoachContext> captor = ArgumentCaptor.forClass(CoachContext.class);
        verify(coachRepo).save(captor.capture());
        assertEquals("coach-1", captor.getValue().getCoachId());
        verify(athleteRepo, never()).save(any());
    }

    @Test
    void upsertMyContext_existingEntry_preservesCreatedAt() {
        mockUser("ath-1", UserRole.ATHLETE);
        AthleteContext existing = new AthleteContext();
        existing.setId("ctx-1");
        existing.setAthleteId("ath-1");
        existing.setAuthorId("ath-1");
        existing.setAuthorRole(ContextAuthorRole.ATHLETE);
        existing.setCreatedAt(java.time.LocalDateTime.of(2020, 1, 1, 0, 0));
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "ath-1")).thenReturn(Optional.of(existing));
        when(athleteRepo.save(any(AthleteContext.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsertMyContext("ath-1", sections(), Provenance.web());

        ArgumentCaptor<AthleteContext> captor = ArgumentCaptor.forClass(AthleteContext.class);
        verify(athleteRepo).save(captor.capture());
        assertEquals(java.time.LocalDateTime.of(2020, 1, 1, 0, 0), captor.getValue().getCreatedAt());
        assertEquals("ctx-1", captor.getValue().getId());
    }

    @Test
    void getMyAthleteContext_readsSelfEntryWithoutResolvingRole() {
        AthleteContext self = new AthleteContext();
        self.setSections(sections());
        when(athleteRepo.findByAthleteIdAndAuthorId("u-1", "u-1")).thenReturn(Optional.of(self));

        ContextService.MyContext result = service.getMyAthleteContext("u-1");

        assertEquals(UserRole.ATHLETE.name(), result.role());
        assertEquals(sections(), result.sections());
        // A coach is also an athlete: this path never gates on role.
        verifyNoInteractions(userService);
        verify(coachRepo, never()).findByCoachId(any());
    }

    @Test
    void upsertMyAthleteContext_asCoach_writesSelfAuthoredAthleteEntry() {
        // A coach editing the Training > Context page writes their OWN athlete self-context,
        // not their coaching philosophy. Role is never consulted on this path.
        when(athleteRepo.findByAthleteIdAndAuthorId("coach-1", "coach-1")).thenReturn(Optional.empty());
        when(athleteRepo.save(any(AthleteContext.class))).thenAnswer(inv -> inv.getArgument(0));

        ContextService.MyContext result = service.upsertMyAthleteContext("coach-1", sections(), Provenance.web());

        assertEquals(UserRole.ATHLETE.name(), result.role());
        ArgumentCaptor<AthleteContext> captor = ArgumentCaptor.forClass(AthleteContext.class);
        verify(athleteRepo).save(captor.capture());
        AthleteContext saved = captor.getValue();
        assertEquals("coach-1", saved.getAthleteId());
        assertEquals("coach-1", saved.getAuthorId());
        assertEquals(ContextAuthorRole.ATHLETE, saved.getAuthorRole());
        verify(coachRepo, never()).save(any());
        verifyNoInteractions(userService);
    }

    @Test
    void getMyCoachContext_readsPhilosophy() {
        CoachContext phil = new CoachContext();
        phil.setSections(sections());
        when(coachRepo.findByCoachId("coach-1")).thenReturn(Optional.of(phil));

        ContextService.MyContext result = service.getMyCoachContext("coach-1");

        assertEquals(UserRole.COACH.name(), result.role());
        assertEquals(sections(), result.sections());
        verify(athleteRepo, never()).findByAthleteIdAndAuthorId(any(), any());
    }

    @Test
    void upsertMyCoachContext_writesPhilosophy() {
        when(coachRepo.findByCoachId("coach-1")).thenReturn(Optional.empty());
        when(coachRepo.save(any(CoachContext.class))).thenAnswer(inv -> inv.getArgument(0));

        ContextService.MyContext result = service.upsertMyCoachContext("coach-1", sections(), Provenance.web());

        assertEquals(UserRole.COACH.name(), result.role());
        ArgumentCaptor<CoachContext> captor = ArgumentCaptor.forClass(CoachContext.class);
        verify(coachRepo).save(captor.capture());
        assertEquals("coach-1", captor.getValue().getCoachId());
        verify(athleteRepo, never()).save(any());
    }

    @Test
    void getCoachViewOfAthlete_returnsSelfAndOwnEntryOnly() {
        when(coachService.isCoachOfAthlete("coach-1", "ath-1")).thenReturn(true);
        AthleteContext self = new AthleteContext();
        self.setAuthorRole(ContextAuthorRole.ATHLETE);
        self.setSections(Map.of("Body", "no fasted rides"));
        AthleteContext coachEntry = new AthleteContext();
        coachEntry.setAuthorRole(ContextAuthorRole.COACH);
        coachEntry.setSections(Map.of("Plan", "build threshold over 6 weeks"));
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "ath-1")).thenReturn(Optional.of(self));
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "coach-1")).thenReturn(Optional.of(coachEntry));

        ContextService.CoachAthleteContext view = service.getCoachViewOfAthlete("coach-1", "ath-1");

        assertNotNull(view.athleteSelf());
        assertNotNull(view.coachContext());
        assertEquals(Map.of("Body", "no fasted rides"), view.athleteSelf().sections());
        assertEquals(Map.of("Plan", "build threshold over 6 weeks"), view.coachContext().sections());
        // It only ever queries the athlete's own entry and the calling coach's own entry.
        verify(athleteRepo).findByAthleteIdAndAuthorId("ath-1", "ath-1");
        verify(athleteRepo).findByAthleteIdAndAuthorId("ath-1", "coach-1");
        verifyNoMoreInteractions(athleteRepo);
    }

    @Test
    void getCoachViewOfAthlete_byNonCoach_throwsForbidden() {
        when(coachService.isCoachOfAthlete("stranger", "ath-1")).thenReturn(false);
        assertThrows(ForbiddenOperationException.class,
                () -> service.getCoachViewOfAthlete("stranger", "ath-1"));
        verifyNoInteractions(athleteRepo);
    }

    @Test
    void getAthleteSelfContext_neverReturnsCoachAuthoredEntry() {
        // Privacy: self-context lookup keys on authorId == athleteId, so a coach's entry
        // (authorId == coachId) is structurally unreachable through this path.
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "ath-1")).thenReturn(Optional.empty());

        assertTrue(service.getAthleteSelfContext("ath-1").isEmpty());
        verify(athleteRepo).findByAthleteIdAndAuthorId("ath-1", "ath-1");
    }

    @Test
    void upsertCoachAthleteContext_writesCoachAuthoredEntry() {
        when(coachService.isCoachOfAthlete("coach-1", "ath-1")).thenReturn(true);
        when(athleteRepo.findByAthleteIdAndAuthorId("ath-1", "coach-1")).thenReturn(Optional.empty());
        when(athleteRepo.save(any(AthleteContext.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsertCoachAthleteContext("coach-1", "ath-1", sections(), Provenance.mcp());

        ArgumentCaptor<AthleteContext> captor = ArgumentCaptor.forClass(AthleteContext.class);
        verify(athleteRepo).save(captor.capture());
        AthleteContext saved = captor.getValue();
        assertEquals("ath-1", saved.getAthleteId());
        assertEquals("coach-1", saved.getAuthorId());
        assertEquals(ContextAuthorRole.COACH, saved.getAuthorRole());
    }

    @Test
    void upsertCoachAthleteContext_byNonCoach_throwsForbidden() {
        when(coachService.isCoachOfAthlete("stranger", "ath-1")).thenReturn(false);
        assertThrows(ForbiddenOperationException.class,
                () -> service.upsertCoachAthleteContext("stranger", "ath-1", sections(), Provenance.mcp()));
        verify(athleteRepo, never()).save(any());
    }

    @Test
    void upsert_emptySections_throwsValidation() {
        assertThrows(ValidationException.class,
                () -> service.upsertMyContext("ath-1", Map.of(), Provenance.web()));
        verifyNoInteractions(userService);
    }

    @Test
    void upsert_tooManySections_throwsValidation() {
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i < ContextService.MAX_SECTIONS + 1; i++) {
            many.put("s" + i, "v");
        }
        assertThrows(ValidationException.class,
                () -> service.upsertMyContext("ath-1", many, Provenance.web()));
    }

    @Test
    void upsert_sectionTooLong_throwsValidation() {
        Map<String, String> m = Map.of("Body", "x".repeat(ContextService.MAX_SECTION_LENGTH + 1));
        assertThrows(ValidationException.class,
                () -> service.upsertMyContext("ath-1", m, Provenance.web()));
    }
}
