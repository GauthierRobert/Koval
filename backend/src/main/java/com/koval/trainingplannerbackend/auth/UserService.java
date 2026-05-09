package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    /**
     * Single-field reference value update used by club tests. Does not touch unrelated reference values.
     * Returns the previous value (for audit logging) and persists the new value. When the target is
     * {@code POWER_3MIN} or {@code POWER_12MIN}, re-runs the critical-power model so derived
     * {@code criticalPower}/{@code wPrimeJ} stay consistent.
     *
     * @param target the User reference field to update
     * @param customKey for {@code CUSTOM} target only — the key inside {@code customZoneReferenceValues}
     * @param newValue the new integer value (callers responsible for rounding)
     * @return the previous value before the update (null if unset)
     */
    public Integer applyTestReferenceUpdate(String userId,
                                             com.koval.trainingplannerbackend.club.test.ReferenceTarget target,
                                             String customKey,
                                             Integer newValue) {
        User user = getUserById(userId);
        Integer previous = switch (target) {
            case FTP -> user.getFtp();
            case CRITICAL_SWIM_SPEED -> user.getCriticalSwimSpeed();
            case FUNCTIONAL_THRESHOLD_PACE -> user.getFunctionalThresholdPace();
            case PACE_5K -> user.getPace5k();
            case PACE_10K -> user.getPace10k();
            case PACE_HALF_MARATHON -> user.getPaceHalfMarathon();
            case PACE_MARATHON -> user.getPaceMarathon();
            case VO2MAX_POWER -> user.getVo2maxPower();
            case VO2MAX_PACE -> user.getVo2maxPace();
            case POWER_3MIN -> user.getPower3MinW();
            case POWER_12MIN -> user.getPower12MinW();
            case WEIGHT_KG -> user.getWeightKg();
            case CUSTOM -> {
                if (customKey == null || customKey.isBlank()) {
                    throw new IllegalArgumentException("customKey is required for CUSTOM reference target");
                }
                yield user.getCustomZoneReferenceValues().get(customKey);
            }
        };
        switch (target) {
            case FTP -> user.setFtp(newValue);
            case CRITICAL_SWIM_SPEED -> user.setCriticalSwimSpeed(newValue);
            case FUNCTIONAL_THRESHOLD_PACE -> user.setFunctionalThresholdPace(newValue);
            case PACE_5K -> user.setPace5k(newValue);
            case PACE_10K -> user.setPace10k(newValue);
            case PACE_HALF_MARATHON -> user.setPaceHalfMarathon(newValue);
            case PACE_MARATHON -> user.setPaceMarathon(newValue);
            case VO2MAX_POWER -> user.setVo2maxPower(newValue);
            case VO2MAX_PACE -> user.setVo2maxPace(newValue);
            case POWER_3MIN -> {
                user.setPower3MinW(newValue);
                applyCriticalPowerModel(user, user.getPower3MinW(), user.getPower12MinW());
            }
            case POWER_12MIN -> {
                user.setPower12MinW(newValue);
                applyCriticalPowerModel(user, user.getPower3MinW(), user.getPower12MinW());
            }
            case WEIGHT_KG -> user.setWeightKg(newValue);
            case CUSTOM -> {
                if (newValue == null) {
                    user.getCustomZoneReferenceValues().remove(customKey);
                } else {
                    user.getCustomZoneReferenceValues().put(customKey, newValue);
                }
            }
        }
        userRepository.save(user);
        return previous;
    }

    public User updateSettings(String userId, Integer ftp, Integer weightKg, Integer functionalThresholdPace,
            Integer criticalSwimSpeed, Integer pace5k, Integer pace10k,
            Integer paceHalfMarathon, Integer paceMarathon,
            Integer vo2maxPower, Integer vo2maxPace,
            Integer power3MinW, Integer power12MinW,
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
        user.setPower3MinW(power3MinW);
        user.setPower12MinW(power12MinW);
        applyCriticalPowerModel(user, power3MinW, power12MinW);
        if (customZoneReferenceValues != null) {
            user.getCustomZoneReferenceValues().putAll(customZoneReferenceValues);
        }
        user.setAiPrePrompt(aiPrePrompt);
        if (aiPrePromptEnabled != null) {
            user.setAiPrePromptEnabled(aiPrePromptEnabled);
        }
        return userRepository.save(user);
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
        return userRepository.save(user);
    }

    public User acceptCgu(String userId) {
        User user = getUserById(userId);
        user.setCguAcceptedAt(LocalDateTime.now());
        user.setCguVersion(CguConstants.CURRENT_VERSION);
        return userRepository.save(user);
    }

    /**
     * Derives critical power (CP) and W' (anaerobic work capacity) from 3-min and 12-min all-out
     * power tests using the two-parameter Monod–Scherrer / Skiba linear model:
     *   P = CP + W'/t  →  CP = (4·P12 − P3) / 3,  W' = (P3 − CP) · 180s.
     * Clears both values if either input is missing or the model is invalid (requires P3 > P12 > 0).
     */
    private static void applyCriticalPowerModel(User user, Integer power3MinW, Integer power12MinW) {
        if (power3MinW != null && power12MinW != null
                && power3MinW > 0 && power12MinW > 0
                && power3MinW > power12MinW) {
            int cp = Math.round((4f * power12MinW - power3MinW) / 3f);
            user.setCriticalPower(cp);
            user.setWPrimeJ(Math.round((power3MinW - cp) * 180f));
        } else {
            user.setCriticalPower(null);
            user.setWPrimeJ(null);
        }
    }
}
