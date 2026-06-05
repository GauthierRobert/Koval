package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.ThresholdReferenceChangedEvent;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionMetricsBackfillServiceTest {

    private static final String USER_ID = "u1";

    @Mock
    private CompletedSessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;

    private AnalyticsService analyticsService;
    private SessionMetricsBackfillService service;

    @BeforeEach
    void setUp() {
        analyticsService = spy(new AnalyticsService(sessionRepository, userRepository));
        service = new SessionMetricsBackfillService(sessionRepository, userRepository, analyticsService);
    }

    private User userWithFtp(Integer ftp) {
        User u = new User();
        u.setId(USER_ID);
        u.setFtp(ftp);
        return u;
    }

    private CompletedSession cyclingSession(double avgPower) {
        CompletedSession s = new CompletedSession();
        s.setUserId(USER_ID);
        s.setSportType("CYCLING");
        s.setTotalDurationSeconds(3600);
        s.setAvgPower(avgPower);
        return s;
    }

    private void givenUserAndCandidates(User user, CompletedSession... candidates) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(sessionRepository.findMetricsBackfillCandidatesByUserId(USER_ID))
                .thenReturn(List.of(candidates));
    }

    @Test
    void nullTssSession_ftpNowSet_computesAndSaves() {
        CompletedSession s = cyclingSession(200);
        givenUserAndCandidates(userWithFtp(250), s);

        service.backfillMissingMetrics(USER_ID);

        assertEquals(0.8, s.getIntensityFactor(), 0.001);
        assertEquals(64.0, s.getTss(), 0.1);
        assertEquals(Boolean.FALSE, s.getTssFromRpe());
        verify(sessionRepository).save(s);
        verify(analyticsService).recomputeAndSaveUserLoad(USER_ID);
    }

    @Test
    void rpeEstimatedSession_upgradedToPowerBasedTss() {
        CompletedSession s = cyclingSession(250);
        s.setRpe(5);
        s.setTss(54.2); // previous RPE-derived estimate
        s.setIntensityFactor(0.736);
        s.setTssFromRpe(true);
        givenUserAndCandidates(userWithFtp(250), s);

        service.backfillMissingMetrics(USER_ID);

        assertEquals(1.0, s.getIntensityFactor(), 0.001);
        assertEquals(100.0, s.getTss(), 0.1);
        assertEquals(Boolean.FALSE, s.getTssFromRpe());
        verify(sessionRepository).save(s);
    }

    @Test
    void noPowerNoRpe_remainsNull_notSaved() {
        CompletedSession s = cyclingSession(0);
        givenUserAndCandidates(userWithFtp(250), s);

        service.backfillMissingMetrics(USER_ID);

        assertNull(s.getTss());
        verify(sessionRepository, never()).save(any());
        verify(analyticsService, never()).recomputeAndSaveUserLoad(any());
    }

    @Test
    void rpeFallbackReproducesSameValues_notSaved() {
        // No power signal: recompute falls back to the same RPE estimate — no pointless write.
        CompletedSession s = cyclingSession(0);
        s.setRpe(7);
        s.setTss(72.2); // matches what the RPE fallback recomputes (IF 0.85 over 1h, rounded)
        s.setIntensityFactor(0.85);
        s.setTssFromRpe(true);
        givenUserAndCandidates(userWithFtp(250), s);

        service.backfillMissingMetrics(USER_ID);

        verify(sessionRepository, never()).save(any());
        verify(analyticsService, never()).recomputeAndSaveUserLoad(any());
    }

    @Test
    void recomputeYieldsNothing_previousValuesRestored() {
        // RPE was cleared since the estimate was stored and there is no power signal:
        // the recompute produces nothing, so the stored estimate must survive.
        CompletedSession s = cyclingSession(0);
        s.setTss(54.2);
        s.setIntensityFactor(0.736);
        s.setTssFromRpe(true);
        givenUserAndCandidates(userWithFtp(250), s);

        service.backfillMissingMetrics(USER_ID);

        assertEquals(54.2, s.getTss(), 0.001);
        assertEquals(0.736, s.getIntensityFactor(), 0.001);
        assertEquals(Boolean.TRUE, s.getTssFromRpe());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void unknownUser_doesNothing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.backfillMissingMetrics(USER_ID);

        verify(sessionRepository, never()).findMetricsBackfillCandidatesByUserId(any());
    }

    @Test
    void eventListener_delegatesAndSwallowsErrors() {
        when(userRepository.findById(USER_ID)).thenThrow(new IllegalStateException("boom"));

        assertDoesNotThrow(() ->
                service.onThresholdReferenceChanged(new ThresholdReferenceChangedEvent(USER_ID)));
    }
}
