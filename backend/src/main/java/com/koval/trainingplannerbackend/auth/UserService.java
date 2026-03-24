package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final GroupService groupService;

    public UserService(UserRepository userRepository, GroupService groupService) {
        this.userRepository = userRepository;
        this.groupService = groupService;
    }

    public User findOrCreateFromStrava(String stravaId, String displayName, String profilePicture,
            String accessToken, String refreshToken, Long expiresAt) {
        Optional<User> existing = userRepository.findByStravaId(stravaId);

        if (existing.isPresent()) {
            User user = existing.get();
            user.setStravaAccessToken(accessToken);
            user.setStravaRefreshToken(refreshToken);
            user.setStravaTokenExpiresAt(expiresAt);
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        }

        User newUser = new User();
        newUser.setStravaId(stravaId);
        newUser.setAuthProvider(AuthProvider.STRAVA);
        newUser.setDisplayName(displayName);
        newUser.setProfilePicture(profilePicture);
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

    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public Optional<User> findById(String userId) {
        return userRepository.findById(userId);
    }

    public List<User> findAllById(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return userRepository.findAllById(userIds);
    }

    public Optional<User> findByStravaId(String stravaId) {
        return userRepository.findByStravaId(stravaId);
    }

    public User setRole(String userId, UserRole role) {
        User user = getUserById(userId);
        user.setRole(role);
        return userRepository.save(user);
    }

    public User updateFtp(String userId, Integer ftp) {
        User user = getUserById(userId);
        user.setFtp(ftp);
        return userRepository.save(user);
    }

    public User updateProfile(String userId, String displayName, String email) {
        User user = getUserById(userId);
        if (displayName != null)
            user.setDisplayName(displayName);
        if (email != null)
            user.setEmail(email);
        return userRepository.save(user);
    }

    public User setCustomZoneReference(String userId, String zoneSystemId, int value) {
        User user = getUserById(userId);
        user.getCustomZoneReferenceValues().put(zoneSystemId, value);
        return userRepository.save(user);
    }

    public User updateSettings(String userId, Integer ftp, Integer weightKg, Integer functionalThresholdPace,
            Integer criticalSwimSpeed, Integer pace5k, Integer pace10k,
            Integer paceHalfMarathon, Integer paceMarathon,
            Integer vo2maxPower, Integer vo2maxPace,
            Map<String, Integer> customZoneReferenceValues,
            String aiPrePrompt, Boolean aiPrePromptEnabled) {
        User user = getUserById(userId);
        // Always set all fields — null means "clear this value"
        user.setFtp(ftp);
        user.setWeightKg(weightKg);
        user.setFunctionalThresholdPace(functionalThresholdPace);
        user.setCriticalSwimSpeed(criticalSwimSpeed);
        user.setPace5k(pace5k);
        user.setPace10k(pace10k);
        user.setPaceHalfMarathon(paceHalfMarathon);
        user.setPaceMarathon(paceMarathon);
        user.setVo2maxPower(vo2maxPower);
        user.setVo2maxPace(vo2maxPace);
        if (customZoneReferenceValues != null) {
            user.getCustomZoneReferenceValues().putAll(customZoneReferenceValues);
        }
        user.setAiPrePrompt(aiPrePrompt);
        if (aiPrePromptEnabled != null) {
            user.setAiPrePromptEnabled(aiPrePromptEnabled);
        }
        return userRepository.save(user);
    }

    public User unlinkStrava(String userId) {
        User user = getUserById(userId);
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
        User user = getUserById(userId);
        if (user.getStravaId() == null) {
            throw new IllegalStateException("Cannot unlink Google — it's your only login method");
        }
        user.setGoogleId(null);
        user.setEmail(null);
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            user.setAuthProvider(AuthProvider.STRAVA);
        }
        return userRepository.save(user);
    }

    /**
     * Convert a User entity to a Map suitable for JSON responses.
     * Includes tag information and coach-specific metadata.
     */
    public Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("displayName", user.getDisplayName());
        map.put("profilePicture", user.getProfilePicture());
        map.put("role", user.getRole().name());
        map.put("ftp", user.getFtp());
        map.put("weightKg", user.getWeightKg());
        map.put("functionalThresholdPace", user.getFunctionalThresholdPace());
        map.put("criticalSwimSpeed", user.getCriticalSwimSpeed());
        map.put("pace5k", user.getPace5k());
        map.put("pace10k", user.getPace10k());
        map.put("paceHalfMarathon", user.getPaceHalfMarathon());
        map.put("paceMarathon", user.getPaceMarathon());
        map.put("vo2maxPower", user.getVo2maxPower());
        map.put("vo2maxPace", user.getVo2maxPace());

        map.put("hasCoach", groupService.athleteHasCoach(user.getId()));
        List<Group> userGroups = groupService.getGroupsForAthlete(user.getId());
        map.put("groups", userGroups.stream().map(Group::getName).toList());

        map.put("customZoneReferenceValues", user.getCustomZoneReferenceValues());
        map.put("needsOnboarding", user.isNeedsOnboarding());

        // CGU acceptance
        map.put("cguAcceptedAt", user.getCguAcceptedAt());
        map.put("cguVersion", user.getCguVersion());
        boolean needsCgu = user.getCguAcceptedAt() == null
                || !CguConstants.CURRENT_VERSION.equals(user.getCguVersion());
        map.put("needsCguAcceptance", needsCgu);

        Map<String, Boolean> linkedAccounts = new HashMap<>();
        linkedAccounts.put("strava", user.getStravaId() != null);
        linkedAccounts.put("google", user.getGoogleId() != null);
        map.put("linkedAccounts", linkedAccounts);
        map.put("authProvider", user.getAuthProvider() != null ? user.getAuthProvider().name() : null);

        if (user.isCoach()) {
            List<String> athleteIds = groupService.getAthleteIdsForCoach(user.getId());
            map.put("athleteCount", athleteIds.size());
            map.put("aiPrePrompt", user.getAiPrePrompt());
            map.put("aiPrePromptEnabled", user.isAiPrePromptEnabled());
        }
        return map;
    }
}
