package com.koval.trainingplannerbackend.integration.strava;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionFitFileService;
import com.koval.trainingplannerbackend.training.history.SessionService;
import com.koval.trainingplannerbackend.training.history.fit.FitFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StravaActivitySyncServiceTest {

    @Mock
    private StravaApiClient stravaApiClient;
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

    private StravaActivitySyncService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new StravaActivitySyncService(stravaApiClient, sessionRepository,
                sessionService, fitFileService, userRepository, fitFileStore);
        user = new User();
        user.setId("u1");
        user.setStravaRefreshToken("refresh");
        lenient().when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sessionRepository.findStravaActivityIdsByUserId("u1")).thenReturn(List.of());
        lenient().when(stravaApiClient.fetchActivitiesBetween(any(), anyLong(), anyLong()))
                .thenReturn(List.of());
    }

    private static long epoch(LocalDateTime dateTime) {
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    /** now() drifts a little between test setup and the service call — allow a few seconds. */
    private static void assertEpochClose(long expected, long actual) {
        assertTrue(Math.abs(expected - actual) <= 5,
                "expected epoch ~" + expected + " but was " + actual);
    }

    @Test
    void firstImport_windowEndsAtLinkDate_notNow() {
        // Linked 14 days ago: the last 2 weeks arrive via webhook, so a 90-day
        // import must fetch [now-90d, linkedAt] — 2.5 months, not 1 week.
        user.setStravaLinkedAt(LocalDateTime.now().minusDays(14));

        service.importHistory("u1", 90);

        ArgumentCaptor<Long> after = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> before = ArgumentCaptor.forClass(Long.class);
        verify(stravaApiClient).fetchActivitiesBetween(eq(user), after.capture(), before.capture());
        assertEpochClose(epoch(LocalDateTime.now().minusDays(90)), after.getValue());
        assertEpochClose(epoch(user.getStravaLinkedAt()), before.getValue());
        assertNotNull(user.getStravaOldestImportedAt());
        assertEpochClose(epoch(LocalDateTime.now().minusDays(90)), epoch(user.getStravaOldestImportedAt()));
    }

    @Test
    void reImport_sameRange_fetchesNothing() {
        user.setStravaLinkedAt(LocalDateTime.now());
        user.setStravaOldestImportedAt(LocalDateTime.now().minusDays(7));

        StravaActivitySyncService.SyncResult result = service.importHistory("u1", 7);

        assertEquals(0, result.totalFetched());
        assertEquals(0, result.newlyImported());
        verify(stravaApiClient, never()).fetchActivitiesBetween(any(), anyLong(), anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void secondImport_largerRange_fetchesOnlyTheOlderSlice() {
        // Already imported 1 week; asking for 1 month must fetch only the 3 weeks before it.
        user.setStravaLinkedAt(LocalDateTime.now());
        LocalDateTime oldestImported = LocalDateTime.now().minusDays(7);
        user.setStravaOldestImportedAt(oldestImported);

        service.importHistory("u1", 30);

        ArgumentCaptor<Long> after = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> before = ArgumentCaptor.forClass(Long.class);
        verify(stravaApiClient).fetchActivitiesBetween(eq(user), after.capture(), before.capture());
        assertEpochClose(epoch(LocalDateTime.now().minusDays(30)), after.getValue());
        assertEpochClose(epoch(oldestImported), before.getValue());
        assertEpochClose(epoch(LocalDateTime.now().minusDays(30)), epoch(user.getStravaOldestImportedAt()));
    }

    @Test
    void legacyUser_withoutLinkDate_fallsBackToCreatedAt() {
        // Users linked before stravaLinkedAt existed: createdAt bounds the window.
        assertNull(user.getStravaLinkedAt());

        service.importHistory("u1", 30);

        ArgumentCaptor<Long> before = ArgumentCaptor.forClass(Long.class);
        verify(stravaApiClient).fetchActivitiesBetween(eq(user), anyLong(), before.capture());
        assertEpochClose(epoch(user.getCreatedAt()), before.getValue());
    }

    @Test
    void notConnected_throws() {
        user.setStravaRefreshToken(null);

        assertThrows(IllegalStateException.class, () -> service.importHistory("u1", 30));
    }
}
