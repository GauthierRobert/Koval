package com.koval.trainingplannerbackend.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
public class User {
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

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStravaId() {
        return stravaId;
    }

    public void setStravaId(String stravaId) {
        this.stravaId = stravaId;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Integer getFtp() {
        return ftp;
    }

    public void setFtp(Integer ftp) {
        this.ftp = ftp;
    }

    public Double getCtl() {
        return ctl;
    }

    public void setCtl(Double ctl) {
        this.ctl = ctl;
    }

    public Double getAtl() {
        return atl;
    }

    public void setAtl(Double atl) {
        this.atl = atl;
    }

    public Double getTsb() {
        return tsb;
    }

    public void setTsb(Double tsb) {
        this.tsb = tsb;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getStravaAccessToken() {
        return stravaAccessToken;
    }

    public void setStravaAccessToken(String stravaAccessToken) {
        this.stravaAccessToken = stravaAccessToken;
    }

    public String getStravaRefreshToken() {
        return stravaRefreshToken;
    }

    public void setStravaRefreshToken(String stravaRefreshToken) {
        this.stravaRefreshToken = stravaRefreshToken;
    }

    public Long getStravaTokenExpiresAt() {
        return stravaTokenExpiresAt;
    }

    public void setStravaTokenExpiresAt(Long stravaTokenExpiresAt) {
        this.stravaTokenExpiresAt = stravaTokenExpiresAt;
    }

    // Helper methods
    public boolean isCoach() {
        return this.role == UserRole.COACH;
    }
}
