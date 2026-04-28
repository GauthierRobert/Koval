package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tool adapter for user profile information.
 */
@Service
public class McpProfileTools {

    private final UserService userService;

    public McpProfileTools(UserService userService) {
        this.userService = userService;
    }

    @Tool(description = "Get the current user's profile. Returns name, role (ATHLETE or COACH), FTP (Functional Threshold Power in watts), weight, running paces, swim CSS, and training load metrics (CTL/ATL/TSB). Use this to understand the user's fitness level and capabilities before creating workouts.")
    public UserProfile getMyProfile() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        return UserProfile.from(user);
    }

    @Tool(description = "Update the user's FTP (Functional Threshold Power, in watts). Used for cycling TSS/IF calculations. Typical range is 100-400W depending on fitness.")
    public UserProfile updateFtp(
            @ToolParam(description = "FTP in watts (integer)") Integer ftp) {
        if (ftp == null || ftp <= 0) throw new IllegalArgumentException("FTP must be a positive integer.");
        String userId = SecurityUtils.getCurrentUserId();
        return UserProfile.from(userService.updateFtp(userId, ftp));
    }

    @Tool(description = "Update the user's running threshold pace (seconds per kilometer). Used for running TSS/IF calculations. Example: 4:10/km = 250 seconds.")
    public UserProfile updateThresholdPace(
            @ToolParam(description = "Threshold pace in seconds per kilometer") Integer secondsPerKm) {
        if (secondsPerKm == null || secondsPerKm <= 0) throw new IllegalArgumentException("secondsPerKm must be positive.");
        String userId = SecurityUtils.getCurrentUserId();
        User u = userService.getUserById(userId);
        return UserProfile.from(userService.updateSettings(userId,
                u.getFtp(), u.getWeightKg(), secondsPerKm, u.getCriticalSwimSpeed(),
                u.getPace5k(), u.getPace10k(), u.getPaceHalfMarathon(), u.getPaceMarathon(),
                u.getVo2maxPower(), u.getVo2maxPace(),
                u.getPower3MinW(), u.getPower12MinW(),
                u.getCustomZoneReferenceValues(), u.getAiPrePrompt(), u.getAiPrePromptEnabled()));
    }

    @Tool(description = "Update the user's swim Critical Swim Speed (CSS, in seconds per 100m). Used for swim TSS/IF calculations. Example: 1:35/100m = 95 seconds.")
    public UserProfile updateSwimCss(
            @ToolParam(description = "CSS in seconds per 100 meters") Integer secondsPer100m) {
        if (secondsPer100m == null || secondsPer100m <= 0) throw new IllegalArgumentException("secondsPer100m must be positive.");
        String userId = SecurityUtils.getCurrentUserId();
        User u = userService.getUserById(userId);
        return UserProfile.from(userService.updateSettings(userId,
                u.getFtp(), u.getWeightKg(), u.getFunctionalThresholdPace(), secondsPer100m,
                u.getPace5k(), u.getPace10k(), u.getPaceHalfMarathon(), u.getPaceMarathon(),
                u.getVo2maxPower(), u.getVo2maxPace(),
                u.getPower3MinW(), u.getPower12MinW(),
                u.getCustomZoneReferenceValues(), u.getAiPrePrompt(), u.getAiPrePromptEnabled()));
    }

    @Tool(description = "Update the user's body weight in kilograms. Used for power-to-weight ratio and TSS calculations.")
    public UserProfile updateWeight(
            @ToolParam(description = "Weight in kilograms") Integer weightKg) {
        if (weightKg == null || weightKg <= 0 || weightKg > 300) throw new IllegalArgumentException("weightKg must be 1-300.");
        String userId = SecurityUtils.getCurrentUserId();
        User u = userService.getUserById(userId);
        return UserProfile.from(userService.updateSettings(userId,
                u.getFtp(), weightKg, u.getFunctionalThresholdPace(), u.getCriticalSwimSpeed(),
                u.getPace5k(), u.getPace10k(), u.getPaceHalfMarathon(), u.getPaceMarathon(),
                u.getVo2maxPower(), u.getVo2maxPace(),
                u.getPower3MinW(), u.getPower12MinW(),
                u.getCustomZoneReferenceValues(), u.getAiPrePrompt(), u.getAiPrePromptEnabled()));
    }

    public record UserProfile(String id, String displayName, String role,
                               Integer ftp, Integer weightKg,
                               Integer functionalThresholdPace, Integer criticalSwimSpeed,
                               Integer pace5k, Integer pace10k,
                               Double ctl, Double atl, Double tsb) {
        public static UserProfile from(User u) {
            return new UserProfile(
                    u.getId(), u.getDisplayName(),
                    u.getRole() != null ? u.getRole().name() : null,
                    u.getFtp(), u.getWeightKg(),
                    u.getFunctionalThresholdPace(), u.getCriticalSwimSpeed(),
                    u.getPace5k(), u.getPace10k(),
                    u.getCtl(), u.getAtl(), u.getTsb());
        }
    }
}
