package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionFitFileService;
import com.koval.trainingplannerbackend.training.history.SessionService;
import com.koval.trainingplannerbackend.training.history.fit.FitFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuuntoActivitySyncServiceTest {

    @Mock
    private SuuntoOAuthService oauthService;
    @Mock
    private SuuntoApiClient apiClient;
    @Mock
    private CompletedSessionRepository sessionRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private SessionFitFileService fitFileService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FitFileStore fitFileStore;

    private SuuntoActivitySyncService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SuuntoActivitySyncService(oauthService, apiClient, sessionRepository,
                sessionService, fitFileService, userRepository, fitFileStore);
        user = new User();
        user.setId("u1");
        user.setSuuntoUserId("suunto-user");
        user.setSuuntoAccessToken("token");
        lenient().when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(oauthService.ensureValidToken(user)).thenReturn("token");
        lenient().when(sessionRepository.findSuuntoActivityIdsByUserId("u1")).thenReturn(List.of());
        lenient().when(sessionService.saveSession(any(CompletedSession.class), eq("u1"), eq(false)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sessionService.saveSession(any(CompletedSession.class), eq("u1"), eq(true)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(apiClient.exportFit(anyString(), anyString())).thenReturn(Optional.empty());
    }

    private static Map<String, Object> workout(String key) {
        return Map.of("workoutKey", key, "activityId", 3, "totalTime", 3600.0,
                "startTime", 1750000000000L);
    }

    @Test
    void importHistory_newWorkout_savedWithoutNotification() {
        when(apiClient.listWorkouts(eq("token"), anyLong())).thenReturn(List.of(workout("w1")));

        SuuntoActivitySyncService.SyncResult result = service.importHistory("u1");

        assertEquals(1, result.totalFetched());
        assertEquals(1, result.newlyImported());
        verify(sessionService).saveSession(any(CompletedSession.class), eq("u1"), eq(false));
    }

    @Test
    void importHistory_alreadyImported_skipsDuplicate() {
        CompletedSession existing = new CompletedSession();
        existing.setSuuntoActivityId("w1");
        when(sessionRepository.findSuuntoActivityIdsByUserId("u1")).thenReturn(List.of(existing));
        when(apiClient.listWorkouts(eq("token"), anyLong())).thenReturn(List.of(workout("w1")));

        SuuntoActivitySyncService.SyncResult result = service.importHistory("u1");

        assertEquals(0, result.newlyImported());
        assertEquals(1, result.skippedDuplicates());
        verify(sessionService, never()).saveSession(any(), anyString(), eq(false));
    }

    @Test
    void importHistory_fitAvailable_storedAndMetricsRecomputed() {
        when(apiClient.listWorkouts(eq("token"), anyLong())).thenReturn(List.of(workout("w1")));
        when(apiClient.exportFit("token", "w1")).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        service.importHistory("u1");

        verify(fitFileStore).store(any(CompletedSession.class), eq(new byte[]{1, 2, 3}));
        verify(fitFileService).recomputeMetricsAfterFitChange(any(CompletedSession.class));
    }

    @Test
    void importHistory_fitStoreFails_sessionStillImported() {
        when(apiClient.listWorkouts(eq("token"), anyLong())).thenReturn(List.of(workout("w1")));
        when(apiClient.exportFit("token", "w1")).thenReturn(Optional.of(new byte[]{1}));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(fitFileStore).store(any(), any());

        SuuntoActivitySyncService.SyncResult result = service.importHistory("u1");

        assertEquals(1, result.newlyImported());
        assertEquals(0, result.skippedErrors());
    }

    @Test
    void importHistory_notConnected_throws() {
        user.setSuuntoUserId(null);

        assertThrows(IllegalStateException.class, () -> service.importHistory("u1"));
    }

    @Test
    void importSingleWorkout_newWorkout_savedWithNotification() {
        when(apiClient.fetchWorkout("token", "w9")).thenReturn(Map.of("activityId", 2));

        service.importSingleWorkout(user, "w9");

        verify(sessionService).saveSession(any(CompletedSession.class), eq("u1"), eq(true));
    }

    @Test
    void importSingleWorkout_alreadyImported_skips() {
        CompletedSession existing = new CompletedSession();
        existing.setSuuntoActivityId("w9");
        when(sessionRepository.findSuuntoActivityIdsByUserId("u1")).thenReturn(List.of(existing));

        service.importSingleWorkout(user, "w9");

        verify(apiClient, never()).fetchWorkout(anyString(), anyString());
        verify(sessionService, never()).saveSession(any(), anyString(), eq(true));
    }
}
