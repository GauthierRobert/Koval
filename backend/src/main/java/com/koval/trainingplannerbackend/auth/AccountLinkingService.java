package com.koval.trainingplannerbackend.auth;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AccountLinkingService {

    private final UserRepository userRepository;
    private final UserService userService;

    public AccountLinkingService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    public User findOrCreateFromStrava(String stravaId, String displayName, String profilePicture,
            String accessToken, String refreshToken, Long expiresAt, String email) {
        Optional<User> existing = userRepository.findByStravaId(stravaId);

        if (existing.isPresent()) {
            User user = existing.get();
            user.setStravaAccessToken(accessToken);
            user.setStravaRefreshToken(refreshToken);
            user.setStravaTokenExpiresAt(expiresAt);
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        }

        // Reconcile by email: if a user with the same email exists (e.g. from Google), link Strava
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                user.setStravaId(stravaId);
                user.setStravaAccessToken(accessToken);
                user.setStravaRefreshToken(refreshToken);
                user.setStravaTokenExpiresAt(expiresAt);
                user.setLastLogin(LocalDateTime.now());
                return userRepository.save(user);
            }
        }

        User newUser = new User();
        newUser.setStravaId(stravaId);
        newUser.setAuthProvider(AuthProvider.STRAVA);
        newUser.setDisplayName(displayName);
        newUser.setProfilePicture(profilePicture);
        newUser.setEmail(email);
        newUser.setStravaAccessToken(accessToken);
        newUser.setStravaRefreshToken(refreshToken);
        newUser.setStravaTokenExpiresAt(expiresAt);
        newUser.setRole(UserRole.ATHLETE);
        newUser.setLastLogin(LocalDateTime.now());
        newUser.setNeedsOnboarding(true);

        return userRepository.save(newUser);
    }

    public User findOrCreateFromGoogle(String googleId, String displayName, String email, String profilePicture) {
        Optional<User> existing = userRepository.findByGoogleId(googleId);

        if (existing.isPresent()) {
            User user = existing.get();
            user.setDisplayName(displayName);
            user.setEmail(email);
            user.setProfilePicture(profilePicture);
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        }

        // Reconcile by email: if a user with the same email exists (e.g. from Strava), link Google to that account
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                user.setGoogleId(googleId);
                user.setDisplayName(displayName);
                user.setProfilePicture(profilePicture);
                user.setLastLogin(LocalDateTime.now());
                return userRepository.save(user);
            }
        }

        User newUser = new User();
        newUser.setGoogleId(googleId);
        newUser.setAuthProvider(AuthProvider.GOOGLE);
        newUser.setDisplayName(displayName);
        newUser.setEmail(email);
        newUser.setProfilePicture(profilePicture);
        newUser.setRole(UserRole.ATHLETE);
        newUser.setLastLogin(LocalDateTime.now());
        newUser.setNeedsOnboarding(true);

        return userRepository.save(newUser);
    }

    // ── Linking ──────────────────────────────────────────────────────────

    public User linkStrava(String userId, String stravaId, String accessToken,
                           String refreshToken, Long expiresAt) {
        // Ensure stravaId is not already used by another user
        userRepository.findByStravaId(stravaId).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new IllegalStateException("This Strava account is already linked to another user");
            }
        });
        User user = userService.getUserById(userId);
        user.setStravaId(stravaId);
        user.setStravaAccessToken(accessToken);
        user.setStravaRefreshToken(refreshToken);
        user.setStravaTokenExpiresAt(expiresAt);
        return userRepository.save(user);
    }

    public User linkGoogle(String userId, String googleId, String email) {
        userRepository.findByGoogleId(googleId).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new IllegalStateException("This Google account is already linked to another user");
            }
        });
        User user = userService.getUserById(userId);
        user.setGoogleId(googleId);
        user.setEmail(email);
        return userRepository.save(user);
    }

    public User linkGarmin(String userId, String garminUserId, String accessToken, String accessTokenSecret) {
        userRepository.findByGarminUserId(garminUserId).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new IllegalStateException("This Garmin account is already linked to another user");
            }
        });
        User user = userService.getUserById(userId);
        user.setGarminUserId(garminUserId);
        user.setGarminAccessToken(accessToken);
        user.setGarminAccessTokenSecret(accessTokenSecret);
        return userRepository.save(user);
    }

    public User unlinkGarmin(String userId) {
        User user = userService.getUserById(userId);
        user.setGarminUserId(null);
        user.setGarminAccessToken(null);
        user.setGarminAccessTokenSecret(null);
        user.setGarminLastSyncAt(null);
        return userRepository.save(user);
    }

    public User linkZwift(String userId, String zwiftUserId, String accessToken, String refreshToken) {
        userRepository.findByZwiftUserId(zwiftUserId).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new IllegalStateException("This Zwift account is already linked to another user");
            }
        });
        User user = userService.getUserById(userId);
        user.setZwiftUserId(zwiftUserId);
        user.setZwiftAccessToken(accessToken);
        user.setZwiftRefreshToken(refreshToken);
        return userRepository.save(user);
    }

    public User unlinkZwift(String userId) {
        User user = userService.getUserById(userId);
        user.setZwiftUserId(null);
        user.setZwiftAccessToken(null);
        user.setZwiftRefreshToken(null);
        user.setZwiftLastSyncAt(null);
        return userRepository.save(user);
    }

    // ── Unlinking (existing) ────────────────────────────────────────────

    public User unlinkStrava(String userId) {
        User user = userService.getUserById(userId);
        if (user.getGoogleId() == null) {
            throw new IllegalStateException("Cannot unlink Strava — it's your only login method");
        }
        user.setStravaId(null);
        user.setStravaAccessToken(null);
        user.setStravaRefreshToken(null);
        user.setStravaTokenExpiresAt(null);
        user.setStravaLastSyncAt(null);
        if (user.getAuthProvider() == AuthProvider.STRAVA) {
            user.setAuthProvider(AuthProvider.GOOGLE);
        }
        return userRepository.save(user);
    }

    public User unlinkGoogle(String userId) {
        User user = userService.getUserById(userId);
        if (user.getStravaId() == null) {
            throw new IllegalStateException("Cannot unlink Google — it's your only login method");
        }
        user.setGoogleId(null);
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            user.setAuthProvider(AuthProvider.STRAVA);
        }
        return userRepository.save(user);
    }
}
