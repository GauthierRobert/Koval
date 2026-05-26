package com.koval.trainingplannerbackend.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthControllerTest {

    private OAuthService oAuthService;
    private OAuthController controller;

    @BeforeEach
    void setUp() {
        oAuthService = mock(OAuthService.class);
        controller = new OAuthController(oAuthService);
        ReflectionTestUtils.setField(controller, "issuer", "http://localhost:8080");
        ReflectionTestUtils.setField(controller, "jwtSecret", "0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    @Test
    void authorize_withoutToken_redirectsToFrontendConsentPage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<Void> response = controller.authorize(
                "client-123", "https://claude.ai/callback", "code",
                "challenge", "S256", "state-xyz", null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().getFirst("Location");
        assertThat(location).startsWith("http://localhost:4200/oauth/authorize");
        assertThat(location).contains("client_id=client-123");
        assertThat(location).contains("response_type=code");
        assertThat(location).contains("code_challenge=challenge");
        assertThat(location).contains("state=state-xyz");
        // The old broken behaviour wrapped everything in /login?returnTo=
        assertThat(location).doesNotContain("/login");
    }

    @Test
    void clientInfo_returnsClientName() {
        when(oAuthService.clientName("client-123")).thenReturn(Optional.of("Claude"));

        ResponseEntity<Map<String, Object>> response = controller.clientInfo("client-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("clientName", "Claude");
    }

    @Test
    void clientInfo_unknownClient_returns404() {
        when(oAuthService.clientName("nope")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.clientInfo("nope");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
