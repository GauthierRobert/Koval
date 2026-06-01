package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import com.koval.trainingplannerbackend.race.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for race classification side-effects on {@link SessionService}. */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "sess-1";
    private static final String RACE_ID = "race-1";

    @Mock private CompletedSessionRepository repository;
    @Mock private AnalyticsService analyticsService;
    @Mock private UserRepository userRepository;
    @Mock private CoachService coachService;
    @Mock private ScheduledWorkoutService scheduledWorkoutService;
    @Mock private SessionAssociationService associationService;
    @Mock private ClubTrainingSessionRepository clubTrainingSessionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SessionFitFileService fitFileService;
    @Mock private RaceGoalService raceGoalService;

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(repository, analyticsService, userRepository, coachService,
                scheduledWorkoutService, associationService, clubTrainingSessionRepository,
                eventPublisher, fitFileService, raceGoalService);
    }

    private CompletedSession ownedSession(String title) {
        CompletedSession session = new CompletedSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setTitle(title);
        when(repository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(repository.save(any(CompletedSession.class))).thenAnswer(i -> i.getArgument(0));
        return session;
    }

    private Race race(String title) {
        Race race = new Race();
        race.setId(RACE_ID);
        race.setTitle(title);
        return race;
    }

    @Test
    void classifyRace_asRace_overwritesTitleWithRaceNameAndBundles() {
        ownedSession("Morning Ride");
        when(raceGoalService.isRaceInGoals(USER_ID, RACE_ID)).thenReturn(true);
        when(raceGoalService.findGoalRace(USER_ID, RACE_ID)).thenReturn(race("Ironman Nice"));

        CompletedSession result = service.classifyRace(SESSION_ID, RACE_ID, RaceRole.RACE, USER_ID);

        assertEquals("Ironman Nice", result.getTitle());
        assertEquals(RACE_ID, result.getRaceId());
        assertEquals(RaceRole.RACE, result.getRaceRole());
        assertEquals("race-" + RACE_ID, result.getGroupId());
    }

    @Test
    void classifyRace_asWarmup_leavesTitleUntouched() {
        ownedSession("Morning Ride");
        when(raceGoalService.isRaceInGoals(USER_ID, RACE_ID)).thenReturn(true);

        CompletedSession result = service.classifyRace(SESSION_ID, RACE_ID, RaceRole.WARMUP, USER_ID);

        assertEquals("Morning Ride", result.getTitle());
        assertEquals(RACE_ID, result.getRaceId());
        assertEquals(RaceRole.WARMUP, result.getRaceRole());
        assertNull(result.getGroupId());
        verify(raceGoalService, never()).findGoalRace(any(), any());
    }

    @Test
    void unclassifyRace_clearsRaceFieldsAndRaceDerivedGroup() {
        CompletedSession session = ownedSession("Ironman Nice");
        session.setRaceId(RACE_ID);
        session.setRaceRole(RaceRole.RACE);
        session.setGroupId("race-" + RACE_ID);

        CompletedSession result = service.unclassifyRace(SESSION_ID, USER_ID);

        assertNull(result.getRaceId());
        assertNull(result.getRaceRole());
        assertNull(result.getGroupId());
        // Title is intentionally preserved — a prior RACE classification renamed it to the event.
        assertEquals("Ironman Nice", result.getTitle());
    }

    @Test
    void unclassifyRace_keepsUserSetBrickGroup() {
        CompletedSession session = ownedSession("Brick");
        session.setRaceId(RACE_ID);
        session.setRaceRole(RaceRole.WARMUP);
        session.setGroupId("brick-2026-06-01");

        CompletedSession result = service.unclassifyRace(SESSION_ID, USER_ID);

        assertNull(result.getRaceId());
        assertNull(result.getRaceRole());
        assertEquals("brick-2026-06-01", result.getGroupId());
    }
}
