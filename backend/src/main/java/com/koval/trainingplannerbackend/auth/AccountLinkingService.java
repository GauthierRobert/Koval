package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.integration.terra.TerraApiClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AccountLinkingService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final TerraApiClient terraApiClient;

    public AccountLinkingService(UserRepository userRepository,
                                 UserService userService,
                                 TerraApiClient terraApiClient) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.terraApiClient = terraApiClient;
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

    public User findOrCreateFromPolar(String polarUserId, String displayName,
                                      String accessToken, String refreshToken, Long expiresAt) {
        Optional<User> existing = userRepository.findByPolarUserId(polarUserId);

        if (existing.isPresent()) {
            User user = existing.get();
            user.setPolarAccessToken(accessToken);
            if (refreshToken != null) user.setPolarRefreshToken(refreshToken);
            if (expiresAt != null) user.setPolarTokenExpiresAt(expiresAt);
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        }

        User newUser = new User();
        newUser.setPolarUserId(polarUserId);
        newUser.setAuthProvider(AuthProvider.POLAR);
        newUser.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : "Polar athlete");
        newUser.setPolarAccessToken(accessToken);
        newUser.setPolarRefreshToken(refreshToken);
        newUser.setPolarTokenExpiresAt(expiresAt);
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

    public User linkPolar(String userId, String polarUserId, String accessToken,
                          String refreshToken, Long expiresAt) {
        userRepository.findByPolarUserId(polarUserId).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new IllegalStateException("This Polar account is already linked to another user");
            }
        });
        User user = userService.getUserById(userId);
        user.setPolarUserId(polarUserId);
        user.setPolarAccessToken(accessToken);
        user.setPolarRefreshToken(refreshToken);
        user.setPolarTokenExpiresAt(expiresAt);
        return userRepository.save(user);
    }

    public User unlinkPolar(String userId) {
        User user = userService.getUserById(userId);
        user.setPolarUserId(null);
        user.setPolarAccessToken(null);
        user.setPolarRefreshToken(null);
        user.setPolarTokenExpiresAt(null);
        user.setPolarLastSyncAt(null);
        user.setPolarAutoPushWorkouts(false);
        return userRepository.save(user);
    }

    public User findOrCreateFromSuunto(String suuntoUserId, String displayName,
                                       String accessToken, String refreshToken, Long expiresAt) {
        Optional<User> existing = userRepository.findBySuuntoUserId(suuntoUserId);

        if (existing.isPresent()) {
            User user = existing.get();
            user.setSuuntoAccessToken(accessToken);
            if (refreshToken != null) user.setSuuntoRefreshToken(refreshToken);
            if (expiresAt != null) user.setSuuntoTokenExpiresAt(expiresAt);
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        }

        User newUser = new User();
        newUser.setSuuntoUserId(suuntoUserId);
        newUser.setAuthProvider(AuthProvider.SUUNTO);
        newUser.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : "Suunto athlete");
        newUser.setSuuntoAccessToken(accessToken);
        newUser.setSuuntoRefreshToken(refreshToken);
        newUser.setSuuntoTokenExpiresAt(expiresAt);
        newUser.setRole(UserRole.ATHLETE);
        newUser.setLastLogin(LocalDateTime.now());
        newUser.setNeedsOnboarding(true);

        return userRepository.save(newUser);
    }

    public User linkSuunto(String userId, String suuntoUserId, String accessToken,
                           String refreshToken, Long expiresAt) {
        userRepository.findBySuuntoUserId(suuntoUserId).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new IllegalStateException("This Suunto account is already linked to another user");
            }
        });
        User user = userService.getUserById(userId);
        user.setSuuntoUserId(suuntoUserId);
        user.setSuuntoAccessToken(accessToken);
        user.setSuuntoRefreshToken(refreshToken);
        user.setSuuntoTokenExpiresAt(expiresAt);
        return userRepository.save(user);
    }

    public User unlinkSuunto(String userId) {
        User user = userService.getUserById(userId);
        user.setSuuntoUserId(null);
        user.setSuuntoAccessToken(null);
        user.setSuuntoRefreshToken(null);
        user.setSuuntoTokenExpiresAt(null);
        user.setSuuntoLastSyncAt(null);
        user.setSuuntoAutoPushWorkouts(false);
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

    /**
     * Disconnects the Nolio activity feed (Terra read side).
     * Also attempts Terra-side deauth; local state is cleared regardless.
     */
    public User unlinkNolioRead(String userId) {
        User user = userService.getUserById(userId);
        if (user.getTerraUserId() != null) {
            terraApiClient.deauthenticateUser(user.getTerraUserId());
        }
        user.setTerraUserId(null);
        user.setTerraProviderNolioConnected(false);
        return userRepository.save(user);
    }

    /**
     * Disconnects the direct Nolio write access and clears the auto-sync flag.
     * Leaves the Terra read side untouched — the two connections are independent.
     */
    public User unlinkNolioWrite(String userId) {
        User user = userService.getUserById(userId);
        user.setNolioUserId(null);
        user.setNolioAccessToken(null);
        user.setNolioRefreshToken(null);
        user.setNolioTokenExpiresAt(null);
        user.setNolioLastSyncAt(null);
        user.setNolioAutoSyncWorkouts(false);
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
