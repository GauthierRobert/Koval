package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.zone.ZoneAutoGenerationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ZoneAutoGenerationService zoneAutoGenerationService;

    public UserService(UserRepository userRepository, ZoneAutoGenerationService zoneAutoGenerationService) {
        this.userRepository = userRepository;
        this.zoneAutoGenerationService = zoneAutoGenerationService;
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
        User saved = userRepository.save(user);
        zoneAutoGenerationService.generateZonesForUser(saved);
        return saved;
    }

    public User completeOnboarding(String userId, UserRole role, Integer ftp, Integer weightKg,
            Integer criticalSwimSpeed, Integer functionalThresholdPace, Boolean cguAccepted) {
        User user = getUserById(userId);
        if (role != null) user.setRole(role);
        if (ftp != null) user.setFtp(ftp);
        if (weightKg != null) user.setWeightKg(weightKg);
        if (criticalSwimSpeed != null) user.setCriticalSwimSpeed(criticalSwimSpeed);
        if (functionalThresholdPace != null) user.setFunctionalThresholdPace(functionalThresholdPace);
        if (Boolean.TRUE.equals(cguAccepted)) {
            user.setCguAcceptedAt(LocalDateTime.now());
            user.setCguVersion(CguConstants.CURRENT_VERSION);
        }
        user.setNeedsOnboarding(false);
        User saved = userRepository.save(user);
        zoneAutoGenerationService.generateZonesForUser(saved);
        return saved;
    }

    public User acceptCgu(String userId) {
        User user = getUserById(userId);
        user.setCguAcceptedAt(LocalDateTime.now());
        user.setCguVersion(CguConstants.CURRENT_VERSION);
        return userRepository.save(user);
    }

    public User generateCalendarFeedToken(String userId) {
        User user = getUserById(userId);
        user.setCalendarFeedToken(java.util.UUID.randomUUID().toString());
        return userRepository.save(user);
    }

    public User revokeCalendarFeedToken(String userId) {
        User user = getUserById(userId);
        user.setCalendarFeedToken(null);
        return userRepository.save(user);
    }

    public Optional<User> findByCalendarFeedToken(String token) {
        return userRepository.findByCalendarFeedToken(token);
    }
}
