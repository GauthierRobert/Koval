package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.BaseIntegrationTest;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the global COACH-role gate on {@link McpCoachTools}. The MCP tool-call path runs on a
 * request thread whose SecurityContext is stamped by JwtAuthenticationFilter; here we set it
 * directly since we invoke the tool beans without going through the HTTP filter chain.
 */
class McpRoleEnforcementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private McpCoachTools coachTools;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void listAthletes_asAthlete_throwsForbidden() {
        authenticateAs("athlete-1", "ATHLETE");
        assertThatThrownBy(() -> coachTools.listAthletes())
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("COACH");
    }

    @Test
    void appendCoachNote_asAthlete_throwsForbidden() {
        authenticateAs("athlete-1", "ATHLETE");
        assertThatThrownBy(() -> coachTools.appendCoachNote("athlete-2", "looking good", null, null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void listAthletes_asCoach_succeeds() {
        authenticateAs("coach-1", "COACH");
        assertThatCode(() -> {
            List<?> athletes = coachTools.listAthletes();
            assertThat(athletes).isNotNull();
        }).doesNotThrowAnyException();
    }
}
