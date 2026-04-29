package com.koval.trainingplannerbackend.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "storage.gcs")
public class MediaStorageProperties {

    private boolean enabled = false;
    private String bucket;
    private String projectId;
    /**
     * Override the GCS endpoint (e.g. http://localhost:4443 for fake-gcs-server).
     * When set, the client uses anonymous credentials. Leave blank in production.
     */
    private String endpointUrl;
    /**
     * Path to a service-account JSON key file. When set, those credentials are
     * used directly so V4 URL signing has a private key. Use in environments
     * where ADC (e.g. {@code gcloud auth application-default login}) returns
     * user credentials, which cannot sign.
     */
    private String credentialsPath;
    /**
     * Service-account email to impersonate for V4 signing via the IAM
     * {@code signBlob} API. Use in Cloud Run / GKE where the runtime
     * credentials are metadata-server tokens that lack a private key. The
     * runtime SA must hold {@code roles/iam.serviceAccountTokenCreator} on
     * this target SA.
     */
    private String signerServiceAccount;
    private Duration signedUrlUploadTtl = Duration.ofMinutes(15);
    private Duration signedUrlReadTtl = Duration.ofHours(24);
    private long maxUploadSizeBytes = 8 * 1024 * 1024;
    private List<String> allowedContentTypes = List.of(
            "image/jpeg", "image/png", "image/webp", "image/heic");
    private Duration unconfirmedRetention = Duration.ofHours(24);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getCredentialsPath() { return credentialsPath; }
    public void setCredentialsPath(String credentialsPath) { this.credentialsPath = credentialsPath; }

    public String getSignerServiceAccount() { return signerServiceAccount; }
    public void setSignerServiceAccount(String signerServiceAccount) { this.signerServiceAccount = signerServiceAccount; }

    public Duration getSignedUrlUploadTtl() { return signedUrlUploadTtl; }
    public void setSignedUrlUploadTtl(Duration v) { this.signedUrlUploadTtl = v; }

    public Duration getSignedUrlReadTtl() { return signedUrlReadTtl; }
    public void setSignedUrlReadTtl(Duration v) { this.signedUrlReadTtl = v; }

    public long getMaxUploadSizeBytes() { return maxUploadSizeBytes; }
    public void setMaxUploadSizeBytes(long v) { this.maxUploadSizeBytes = v; }

    public List<String> getAllowedContentTypes() { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> v) { this.allowedContentTypes = v; }

    public Duration getUnconfirmedRetention() { return unconfirmedRetention; }
    public void setUnconfirmedRetention(Duration v) { this.unconfirmedRetention = v; }
}
