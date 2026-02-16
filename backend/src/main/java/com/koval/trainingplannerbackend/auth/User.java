package com.koval.trainingplannerbackend.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

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

    private String email;
    private String displayName;
    private String profilePicture;
    private UserRole role = UserRole.ATHLETE;

    private Integer ftp = 250;
    private Double ctl = 0.0;
    private Double atl = 0.0;
    private Double tsb = 0.0;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    // Strava tokens for API access
    private String stravaAccessToken;
    private String stravaRefreshToken;
    private Long stravaTokenExpiresAt;

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    private Integer functionalThresholdPace = 300; // Seconds per km (5:00/km)
    private Integer criticalSwimSpeed = 120; // Seconds per 100m (2:00/100m)
    private Integer pace5k = 270; // Seconds per km (4:30/km)
    private Integer pace10k = 285; // Seconds per km (4:45/km)
    private Integer paceHalfMarathon = 300; // Seconds per km (5:00/km)
    private Integer paceMarathon = 315; // Seconds per km (5:15/km)

    // Helper methods
    public boolean isCoach() {
        return this.role == UserRole.COACH;
    }
}
