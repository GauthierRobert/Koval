package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.BaseIntegrationTest;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.plan.PlanWeek;
import com.koval.trainingplannerbackend.plan.TrainingPlan;
import com.koval.trainingplannerbackend.plan.TrainingPlanService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural checks for the consolidated MCP tools: {@code updateProfile} (one call, partial
 * update), {@code getSessions} (recent vs range modes) and {@code setPlanStatus} (lifecycle
 * routing).
 */
class McpConsolidatedToolsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private McpProfileTools profileTools;
    @Autowired
    private McpHistoryTools historyTools;
    @Autowired
    private McpPlanTools planTools;
    @Autowired
    private UserService userService;
    @Autowired
    private CompletedSessionRepository sessionRepository;
    @Autowired
    private TrainingPlanService planService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_ATHLETE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void updateProfile_partialUpdate_touchesOnlyProvidedFields() throws Exception {
        loginAthlete("athlete-1"); // create the User document via dev login
        authenticateAs("athlete-1");

        // Establish a known baseline across all four reference values.
        profileTools.updateProfile(200, 70, 300, 100);

        // Change only FTP; the other three must survive.
        profileTools.updateProfile(250, null, null, null);

        User u = userService.getUserById("athlete-1");
        assertThat(u.getFtp()).isEqualTo(250);
        assertThat(u.getWeightKg()).isEqualTo(70);
        assertThat(u.getFunctionalThresholdPace()).isEqualTo(300);
        assertThat(u.getCriticalSwimSpeed()).isEqualTo(100);
    }

    @Test
    void getSessions_recentAndRange_returnExpectedSets() {
        authenticateAs("athlete-2");
        saveSession("athlete-2", LocalDate.of(2026, 1, 10).atStartOfDay());
        saveSession("athlete-2", LocalDate.of(2026, 1, 20).atStartOfDay());
        saveSession("athlete-2", LocalDate.of(2026, 2, 15).atStartOfDay());

        var recent = historyTools.getSessions("recent", null, null, 2);
        assertThat(recent).hasSize(2);
        // Most recent first.
        assertThat(recent.get(0).completedAt()).contains("2026-02-15");

        var january = historyTools.getSessions(
                "range", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);
        assertThat(january).hasSize(2);
    }

    @Test
    void setPlanStatus_routesActivateThenPause() {
        TrainingPlan plan = new TrainingPlan();
        plan.setTitle("Status routing plan");
        plan.setSportType(SportType.CYCLING);
        plan.setDurationWeeks(2);
        List<PlanWeek> weeks = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            PlanWeek w = new PlanWeek();
            w.setWeekNumber(i);
            weeks.add(w);
        }
        plan.setWeeks(weeks);
        TrainingPlan created = planService.createPlan(plan, "athlete-3");

        authenticateAs("athlete-3");
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        var activated = planTools.setPlanStatus(created.getId(), "ACTIVE", monday);
        assertThat(activated.status()).isEqualTo("ACTIVE");

        var paused = planTools.setPlanStatus(created.getId(), "PAUSED", null);
        assertThat(paused.status()).isEqualTo("PAUSED");
    }

    private void saveSession(String userId, LocalDateTime completedAt) {
        CompletedSession s = new CompletedSession();
        s.setUserId(userId);
        s.setTitle("Session " + completedAt.toLocalDate());
        s.setSportType("CYCLING");
        s.setCompletedAt(completedAt);
        s.setTotalDurationSeconds(3600);
        sessionRepository.save(s);
    }
}
