package com.koval.trainingplannerbackend.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "firebase.service-account-path")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account-path}")
    private String serviceAccountPath;

    @PostConstruct
    public void init() {
        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            log.warn("Firebase service account path not configured — push notifications disabled");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized");
            return;
        }

        try (FileInputStream fis = new FileInputStream(serviceAccountPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(fis))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase initialized successfully");
        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }
}
