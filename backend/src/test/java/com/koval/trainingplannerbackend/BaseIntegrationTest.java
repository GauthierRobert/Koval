package com.koval.trainingplannerbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests. Provides:
 * - Testcontainers MongoDB
 * - Mock AI layer (no Anthropic key needed)
 * - Helper methods for authentication and common operations
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, MockAIConfig.class})
public abstract class BaseIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    protected MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanDatabase() {
        for (String name : mongoTemplate.getCollectionNames()) {
            mongoTemplate.dropCollection(name);
        }
    }

    /**
     * Dev-login a user and return the JWT token.
     */
    protected String loginUser(String userId, String displayName, String role) throws Exception {
        String body = """
                {"userId": "%s", "displayName": "%s", "role": "%s"}
                """.formatted(userId, displayName, role);

        MvcResult result = mockMvc.perform(post("/api/auth/dev/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    /**
     * Convenience: login as athlete.
     */
    protected String loginAthlete(String userId) throws Exception {
        return loginUser(userId, userId + "-name", "ATHLETE");
    }

    /**
     * Convenience: login as coach.
     */
    protected String loginCoach(String userId) throws Exception {
        return loginUser(userId, userId + "-name", "COACH");
    }

    /**
     * Authorization header value.
     */
    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
