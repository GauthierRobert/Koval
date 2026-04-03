package com.koval.trainingplannerbackend.notification;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native-image hints for Firebase / Google Auth.
 * Registered via @ImportRuntimeHints on FirebaseConfig.
 */
public class FirebaseNativeHints implements RuntimeHintsRegistrar {

    private static final MemberCategory[] ALL = MemberCategory.values();

    // Google Auth and Firebase credential types parsed via reflection
    private static final String[] FIREBASE_TYPES = {
        "com.google.auth.oauth2.GoogleCredentials",
        "com.google.auth.oauth2.ServiceAccountCredentials",
        "com.google.auth.oauth2.ServiceAccountJwtAccessCredentials",
        "com.google.auth.oauth2.UserCredentials",
        "com.google.auth.oauth2.AccessToken",
        "com.google.auth.oauth2.OAuth2Credentials",
        "com.google.auth.http.HttpCredentialsAdapter",
        "com.google.auth.oauth2.ComputeEngineCredentials",
        "com.google.auth.oauth2.ImpersonatedCredentials",
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String type : FIREBASE_TYPES) {
            hints.reflection().registerType(TypeReference.of(type), ALL);
        }

        // Service account JSON is parsed by Gson via reflection
        hints.resources().registerPattern("META-INF/services/com.google.auth.*");
        hints.resources().registerPattern("META-INF/services/com.google.api.client.*");
    }
}
