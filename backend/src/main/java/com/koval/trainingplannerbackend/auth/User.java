package com.koval.trainingplannerbackend.auth;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.koval.trainingplannerbackend.notification.NotificationPreferences;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@Document(collection = "users")
public class User {
    // Getters and Setters
    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String stravaId;

    @Indexed(unique = true, sparse = true)
    private String googleId;

    private AuthProvider authProvider;

    @Indexed
    private String email;
    private String displayName;
    private String profilePicture;
    private UserRole role = UserRole.ATHLETE;

    /**
     * Anonymous public handle (e.g. {@code SwiftOtter-42}) shown wherever this user is visible
     * to others — coach dashboards listing athletes, club member lists, and every MCP tool
     * response. Real name/email never leave the backend except in the user's own profile
     * surface. Generated at account creation; backfilled lazily on first lookup for legacy users.
     */
    @Indexed(unique = true, sparse = true)
    private String alias;

    private Integer ftp;
    private Integer weightKg;
    private Double ctl = 0.0;
    private Double atl = 0.0;
    private Double tsb = 0.0;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    // Strava tokens for API access
    private String stravaAccessToken;
    private String stravaRefreshToken;
    private Long stravaTokenExpiresAt;
    private LocalDateTime stravaLastSyncAt;
    // When Strava was connected. Activities after this arrive via webhook; the manual
    // history import only backfills activities older than this. Null for legacy users
    // (falls back to createdAt).
    private LocalDateTime stravaLinkedAt;
    // Oldest boundary already covered by a manual history import — re-imports only
    // fetch activities older than this, so re-clicking a range never re-scans it.
    private LocalDateTime stravaOldestImportedAt;

    // Garmin tokens (OAuth 1.0a)
    @Indexed(unique = true, sparse = true)
    private String garminUserId;
    private String garminAccessToken;
    private String garminAccessTokenSecret;
    private LocalDateTime garminLastSyncAt;
    private Boolean garminAutoPushWorkouts = false;

    // Polar AccessLink (OAuth 2.0). polarUserId is the numeric id returned by Polar.
    @Indexed(unique = true, sparse = true)
    private String polarUserId;
    private String polarAccessToken;
    private String polarRefreshToken;
    private Long polarTokenExpiresAt;
    private LocalDateTime polarLastSyncAt;

    // Suunto Cloud API (OAuth 2.0). suuntoUserId is the username returned by Suunto.
    @Indexed(unique = true, sparse = true)
    private String suuntoUserId;
    private String suuntoAccessToken;
    private String suuntoRefreshToken;
    private Long suuntoTokenExpiresAt;
    private LocalDateTime suuntoLastSyncAt;
    private Boolean suuntoAutoPushWorkouts = false;

    // Zwift tokens (unofficial API)
    @Indexed(unique = true, sparse = true)
    private String zwiftUserId;
    private String zwiftAccessToken;
    private String zwiftRefreshToken;
    private LocalDateTime zwiftLastSyncAt;
    private Boolean zwiftAutoSyncWorkouts = false;

    // Terra linkage for Nolio activity ingest (read path)
    @Indexed(unique = true, sparse = true)
    private String terraUserId;
    private Boolean terraProviderNolioConnected = false;

    // Nolio direct OAuth tokens (write path - push trainings)
    @Indexed(unique = true, sparse = true)
    private String nolioUserId;
    private String nolioAccessToken;
    private String nolioRefreshToken;
    private Long nolioTokenExpiresAt;
    private LocalDateTime nolioLastSyncAt;
    private Boolean nolioAutoSyncWorkouts = false;

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    private Integer functionalThresholdPace;
    private Integer criticalSwimSpeed;
    private Integer pace5k;
    private Integer pace10k;
    private Integer paceHalfMarathon;
    private Integer paceMarathon;
    private Integer vo2maxPower;   // Watts (VO2MAX_POWER reference)
    private Integer vo2maxPace;    // Seconds per km (VO2MAX_PACE reference)

    // Two-parameter CP test results (max avg power over 3 min and 12 min, in watts).
    // Stored as inputs; criticalPower and wPrimeJ are derived from them.
    private Integer power3MinW;
    private Integer power12MinW;
    private Integer criticalPower; // Watts — derived from the two-test linear model
    private Integer wPrimeJ;       // Joules — derived from the two-test linear model

    private Boolean needsOnboarding = false;

    private LocalDateTime cguAcceptedAt;
    private String cguVersion;

    private String aiPrePrompt;
    private Boolean aiPrePromptEnabled = false;

    private Map<String, Integer> customZoneReferenceValues = new HashMap<>();

    private List<String> fcmTokens = new ArrayList<>();

    private NotificationPreferences notificationPreferences = new NotificationPreferences();

    // Helper methods
    public boolean isCoach() {
        return this.role == UserRole.COACH;
    }
}
