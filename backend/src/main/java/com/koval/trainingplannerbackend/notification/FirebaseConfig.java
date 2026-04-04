package com.koval.trainingplannerbackend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@ImportRuntimeHints(FirebaseNativeHints.class)
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.project-id:}")
    private String projectId;

    private volatile GoogleCredentials credentials;
    private volatile Boolean available;

    private synchronized void ensureInitialized() {
        if (available != null) return;
        try {
            byte[] credentialsBytes = resolveCredentialsBytes();
            if (credentialsBytes == null) {
                log.warn("Firebase credentials not configured — push notifications disabled");
                available = false;
                return;
            }
            // Auto-derive project ID from service account JSON if not explicitly set
            if (projectId == null || projectId.isBlank()) {
                try {
                    JsonNode json = new ObjectMapper().readTree(credentialsBytes);
                    JsonNode pid = json.get("project_id");
                    if (pid != null && !pid.asText().isBlank()) {
                        projectId = pid.asText();
                        log.info("Derived Firebase project ID from service account: {}", projectId);
                    }
                } catch (IOException e) {
                    log.warn("Could not parse project_id from service account JSON: {}", e.getMessage());
                }
            }
            try (InputStream stream = new ByteArrayInputStream(credentialsBytes)) {
                credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(List.of(FCM_SCOPE));
                available = true;
                log.info("FCM credentials initialized successfully");
            }
        } catch (IOException e) {
            log.error("Failed to initialize FCM credentials: {}", e.getMessage());
            available = false;
        }
    }

    private byte[] resolveCredentialsBytes() throws IOException {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            return serviceAccountJson.getBytes(StandardCharsets.UTF_8);
        }
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            try (InputStream is = new FileInputStream(serviceAccountPath)) {
                return is.readAllBytes();
            }
        }
        return null;
    }

    public boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    public String getProjectId() {
        return projectId != null ? projectId.strip() : projectId;
    }

    public String getAccessToken() throws IOException {
        ensureInitialized();
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    @Bean
    public RestClient fcmRestClient() {
        return RestClient.builder()
                .baseUrl("https://fcm.googleapis.com")
                .build();
    }
}
