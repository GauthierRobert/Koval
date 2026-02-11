package com.koval.trainingplannerbackend.auth;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for User management operations.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Find or create a user from Strava OAuth data.
     */
    public User findOrCreateFromStrava(String stravaId, String displayName, String profilePicture,
            String accessToken, String refreshToken, Long expiresAt) {
        Optional<User> existing = userRepository.findByStravaId(stravaId);

        if (existing.isPresent()) {
            User user = existing.get();
            // Update tokens on login
            user.setStravaAccessToken(accessToken);
            user.setStravaRefreshToken(refreshToken);
            user.setStravaTokenExpiresAt(expiresAt);
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        }

        // Create new user
        User newUser = new User();
        newUser.setStravaId(stravaId);
        newUser.setDisplayName(displayName);
        newUser.setProfilePicture(profilePicture);
        newUser.setStravaAccessToken(accessToken);
        newUser.setStravaRefreshToken(refreshToken);
        newUser.setStravaTokenExpiresAt(expiresAt);
        newUser.setRole(UserRole.ATHLETE); // Default role
        newUser.setLastLogin(LocalDateTime.now());

        return userRepository.save(newUser);
    }

    /**
     * Get user by ID.
     */
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    /**
     * Get user by Strava ID.
     */
    public Optional<User> findByStravaId(String stravaId) {
        return userRepository.findByStravaId(stravaId);
    }

    /**
     * Update user's role (ATHLETE or COACH).
     */
    public User setRole(String userId, UserRole role) {
        User user = getUserById(userId);
        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * Update user's FTP.
     */
    public User updateFtp(String userId, Integer ftp) {
        User user = getUserById(userId);
        user.setFtp(ftp);
        return userRepository.save(user);
    }

    /**
     * Update user profile.
     */
    public User updateProfile(String userId, String displayName, String email) {
        User user = getUserById(userId);
        if (displayName != null)
            user.setDisplayName(displayName);
        if (email != null)
            user.setEmail(email);
        return userRepository.save(user);
    }
}
