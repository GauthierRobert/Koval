package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncPayload;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncSourceType;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuuntoWorkoutSyncProviderTest {

    @Mock
    private SuuntoGuideService guideService;

    private SuuntoWorkoutSyncProvider provider;
    private User athlete;
    private WorkoutSyncPayload payload;

    @BeforeEach
    void setUp() {
        provider = new SuuntoWorkoutSyncProvider(guideService);
        athlete = new User();
        athlete.setId("u1");
        athlete.setSuuntoUserId("suunto-user");
        athlete.setSuuntoAccessToken("token");
        athlete.setSuuntoAutoPushWorkouts(true);

        Training training = new CyclingTraining();
        training.setTitle("Workout");
        payload = new WorkoutSyncPayload("u1", WorkoutSyncSourceType.SCHEDULED_WORKOUT, "sw1",
                training, LocalDate.of(2026, 6, 10), "Workout", null);
    }

    @Test
    void isEnabled_requiresOptInAndConnection() {
        assertTrue(provider.isEnabled(athlete));

        athlete.setSuuntoAutoPushWorkouts(false);
        assertFalse(provider.isEnabled(athlete));

        athlete.setSuuntoAutoPushWorkouts(true);
        athlete.setSuuntoAccessToken(null);
        assertFalse(provider.isEnabled(athlete));

        athlete.setSuuntoAccessToken("token");
        athlete.setSuuntoUserId(null);
        assertFalse(provider.isEnabled(athlete));
    }

    @Test
    void push_delegatesToGuideService() {
        when(guideService.pushTraining(eq(athlete), any(), eq(payload.scheduledDate()), eq("sw1")))
                .thenReturn(Optional.of("koval-sw1"));

        assertEquals(Optional.of("koval-sw1"), provider.push(athlete, payload));
    }

    @Test
    void push_serviceThrows_returnsEmpty() {
        when(guideService.pushTraining(any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        assertEquals(Optional.empty(), provider.push(athlete, payload));
    }

    @Test
    void push_missingTrainingOrDate_skipsWithoutCalling() {
        WorkoutSyncPayload noDate = new WorkoutSyncPayload("u1",
                WorkoutSyncSourceType.SCHEDULED_WORKOUT, "sw1", new CyclingTraining(), null, "t", null);

        assertEquals(Optional.empty(), provider.push(athlete, noDate));
        verify(guideService, never()).pushTraining(any(), any(), any(), anyString());
    }

    @Test
    void delete_swallowsErrors() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(guideService).deleteTraining(athlete, "ref");

        provider.delete(athlete, payload, "ref"); // must not throw
    }
}
