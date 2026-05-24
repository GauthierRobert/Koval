package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.context.ContextService;
import com.koval.trainingplannerbackend.context.ContextService.CoachAthleteContext;
import com.koval.trainingplannerbackend.context.ContextService.ContextEntry;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import com.koval.trainingplannerbackend.plan.TrainingPlanService;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpContextToolsTest {

    @Mock private UserService userService;
    @Mock private RaceGoalService raceGoalService;
    @Mock private CompletedSessionRepository sessionRepository;
    @Mock private ScheduledWorkoutService scheduledWorkoutService;
    @Mock private TrainingRepository trainingRepository;
    @Mock private TrainingPlanService planService;
    @Mock private ContextService contextService;
    @Mock private CoachService coachService;

    private McpContextTools tools;

    @BeforeEach
    void setUp() {
        tools = new McpContextTools(userService, raceGoalService, sessionRepository,
                scheduledWorkoutService, trainingRepository, planService, contextService,
                coachService);
        when(raceGoalService.getGoalsForAthlete(any())).thenReturn(List.of());
        when(sessionRepository.findByUserIdAndCompletedAtBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(scheduledWorkoutService.getAthleteSchedule(any(), any(), any())).thenReturn(List.of());
        when(planService.listPlans(any())).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User user(String id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setCtl(40.0);
        u.setAtl(50.0);
        u.setTsb(-10.0);
        return u;
    }

    @Test
    void selfMode_returnsOwnContext_andNoCoachBlocks() {
        authenticateAs("ath-1", "ATHLETE");
        when(userService.getUserById("ath-1")).thenReturn(user("ath-1", UserRole.ATHLETE));
        when(contextService.getAthleteSelfContext("ath-1")).thenReturn(athleteSelf());

        var payload = tools.getAthleteContext(null);

        assertThat(payload.subject().id()).isEqualTo("ath-1");
        assertThat(payload.trainingLoad().atl()).isEqualTo(50.0);
        assertThat(payload.athleteContext()).containsEntry("Voice & communication", "terse");
        assertThat(payload.coachContextAboutAthlete()).isNull();
        assertThat(payload.coachPhilosophy()).isNull();
    }

    @Test
    void coachMode_includesPhilosophyAndPrivateCoachContext() {
        authenticateAs("coach-1", "COACH");
        when(coachService.isCoachOfAthlete("coach-1", "ath-1")).thenReturn(true);
        when(userService.getUserById("ath-1")).thenReturn(user("ath-1", UserRole.ATHLETE));
        when(contextService.getAthleteSelfContext("ath-1")).thenReturn(athleteSelf());
        when(contextService.getCoachPhilosophy("coach-1")).thenReturn(coachPhilosophy());
        when(contextService.getCoachViewOfAthlete("coach-1", "ath-1"))
                .thenReturn(new CoachAthleteContext(
                        new ContextEntry(Map.of("Voice & communication", "terse"), null, null),
                        new ContextEntry(Map.of("Plan for this athlete", "6-week threshold"), null, null)));

        var payload = tools.getAthleteContext("ath-1");

        assertThat(payload.subject().id()).isEqualTo("ath-1");
        assertThat(payload.athleteContext()).containsEntry("Voice & communication", "terse");
        assertThat(payload.coachPhilosophy()).containsEntry("Philosophy", "polarized");
        assertThat(payload.coachContextAboutAthlete())
                .containsEntry("Plan for this athlete", "6-week threshold");
    }

    @Test
    void coachMode_withoutRelationship_throwsForbidden() {
        authenticateAs("coach-1", "COACH");
        when(coachService.isCoachOfAthlete("coach-1", "ath-1")).thenReturn(false);

        assertThatThrownBy(() -> tools.getAthleteContext("ath-1"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void athleteSelfMode_doesNotConsultCoachRelationship() {
        // An athlete loading their own context never triggers a coach check.
        authenticateAs("ath-1", "ATHLETE");
        when(userService.getUserById("ath-1")).thenReturn(user("ath-1", UserRole.ATHLETE));
        when(contextService.getAthleteSelfContext("ath-1")).thenReturn(Optional.empty());

        var payload = tools.getAthleteContext("");

        assertThat(payload.subject().id()).isEqualTo("ath-1");
        assertThat(payload.athleteContext()).isNull();
    }

    private Optional<com.koval.trainingplannerbackend.context.AthleteContext> athleteSelf() {
        var a = new com.koval.trainingplannerbackend.context.AthleteContext();
        a.setAthleteId("ath-1");
        a.setAuthorId("ath-1");
        a.setSections(Map.of("Voice & communication", "terse"));
        a.setUpdatedAt(LocalDateTime.now());
        return Optional.of(a);
    }

    private Optional<com.koval.trainingplannerbackend.context.CoachContext> coachPhilosophy() {
        var c = new com.koval.trainingplannerbackend.context.CoachContext();
        c.setCoachId("coach-1");
        c.setSections(Map.of("Philosophy", "polarized"));
        return Optional.of(c);
    }
}
