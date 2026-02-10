package com.example.trainingplannerbackend.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    private String stravaId;

    private String email;
    private String displayName;
    private String profilePicture;
    private UserRole role = UserRole.ATHLETE;

    // If ATHLETE, the coach's user ID
    private String coachId;

    // If COACH, list of athlete user IDs
    private List<String> athleteIds = new ArrayList<>();

    // Tags for grouping athletes (e.g. "Club BTC", "Junior", "Triathlon")
    private List<String> tags = new ArrayList<>();

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

    public String getCoachId() {
        return coachId;
    }

    public void setCoachId(String coachId) {
        this.coachId = coachId;
    }

    public List<String> getAthleteIds() {
        return athleteIds;
    }

    public void setAthleteIds(List<String> athleteIds) {
        this.athleteIds = athleteIds;
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    // Helper methods
    public boolean isCoach() {
        return this.role == UserRole.COACH;
    }

    public boolean hasCoach() {
        return this.coachId != null && !this.coachId.isEmpty();
    }
}
