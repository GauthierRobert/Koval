package com.koval.trainingplannerbackend.media;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageConfig.class);
    private static final List<String> SIGNING_SCOPES =
            List.of("https://www.googleapis.com/auth/cloud-platform");
    private static final int IMPERSONATION_LIFETIME_SECONDS = 3600;

    @Bean
    @ConditionalOnProperty(prefix = "storage.gcs", name = "enabled", havingValue = "true")
    public Storage storage(MediaStorageProperties properties) throws IOException {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
            builder.setProjectId(properties.getProjectId());
        }

        if (properties.getEndpointUrl() != null && !properties.getEndpointUrl().isBlank()) {
            // Local emulator (fake-gcs-server). No real auth, V4 signing is not enforced.
            builder.setHost(properties.getEndpointUrl())
                    .setCredentials(NoCredentials.getInstance());
            return builder.build().getService();
        }

        if (properties.getCredentialsPath() != null && !properties.getCredentialsPath().isBlank()) {
            try (FileInputStream stream = new FileInputStream(properties.getCredentialsPath())) {
                GoogleCredentials creds = ServiceAccountCredentials.fromStream(stream);
                builder.setCredentials(creds);
            }
            log.info("GCS configured with explicit service-account JSON key (V4 signing native).");
            return builder.build().getService();
        }

        if (properties.getSignerServiceAccount() != null
                && !properties.getSignerServiceAccount().isBlank()) {
            GoogleCredentials source = GoogleCredentials.getApplicationDefault();
            ImpersonatedCredentials impersonated = ImpersonatedCredentials.create(
                    source,
                    properties.getSignerServiceAccount(),
                    null,
                    SIGNING_SCOPES,
                    IMPERSONATION_LIFETIME_SECONDS);
            builder.setCredentials(impersonated);
            log.info("GCS configured to impersonate {} for V4 signing via IAM signBlob.",
                    properties.getSignerServiceAccount());
            return builder.build().getService();
        }

        log.info("GCS configured with Application Default Credentials. "
                + "V4 URL signing requires a service-account key; if ADC has only "
                + "user creds or metadata-server tokens, set storage.gcs.credentials-path "
                + "or storage.gcs.signer-service-account.");
        return builder.build().getService();
    }
}
