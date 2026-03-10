package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TagService tagService;

    public UserService(UserRepository userRepository, TagService tagService) {
        this.userRepository = userRepository;
        this.tagService = tagService;
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
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    public Optional<User> findById(String userId) {
        return userRepository.findById(userId);
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

    public User updateSettings(String userId, Integer ftp, Integer weightKg, Integer functionalThresholdPace,
            Integer criticalSwimSpeed, Integer pace5k, Integer pace10k,
            Integer paceHalfMarathon, Integer paceMarathon,
            Integer vo2maxPower, Integer vo2maxPace) {
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

        map.put("hasCoach", tagService.athleteHasCoach(user.getId()));
        List<Tag> userTags = tagService.getTagsForAthlete(user.getId());
        map.put("tags", userTags.stream().map(Tag::getName).toList());

        map.put("needsOnboarding", user.isNeedsOnboarding());

        if (user.isCoach()) {
            List<String> athleteIds = tagService.getAthleteIdsForCoach(user.getId());
            map.put("athleteCount", athleteIds.size());
        }
        return map;
    }
}
