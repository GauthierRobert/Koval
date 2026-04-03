package com.koval.trainingplannerbackend.notification;

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
            InputStream credentialsStream = resolveCredentials();
            if (credentialsStream == null) {
                log.warn("Firebase credentials not configured — push notifications disabled");
                available = false;
                return;
            }
            try (credentialsStream) {
                credentials = GoogleCredentials.fromStream(credentialsStream)
                        .createScoped(List.of(FCM_SCOPE));
                available = true;
                log.info("FCM credentials initialized successfully");
            }
        } catch (IOException e) {
            log.error("Failed to initialize FCM credentials: {}", e.getMessage());
            available = false;
        }
    }

    private InputStream resolveCredentials() throws IOException {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            return new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        }
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            return new FileInputStream(serviceAccountPath);
        }
        return null;
    }

    public boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    public String getProjectId() {
        return projectId;
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
