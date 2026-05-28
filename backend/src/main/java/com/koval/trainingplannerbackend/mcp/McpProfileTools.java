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

    @Tool(description = "Update the user's training reference values — FTP (watts), body weight (kg), running threshold pace (sec/km), and/or swim CSS (sec/100m). These drive TSS/IF and power-to-weight calculations. Pass only the fields to change; omit (null) the rest. At least one field is required.")
    public UserProfile updateProfile(
            @ToolParam(description = "FTP (Functional Threshold Power) in watts; typical 100-400. Null = unchanged.") Integer ftp,
            @ToolParam(description = "Body weight in kilograms (1-300). Null = unchanged.") Integer weightKg,
            @ToolParam(description = "Running threshold pace in seconds per kilometer (e.g. 4:10/km = 250). Null = unchanged.") Integer thresholdPaceSecPerKm,
            @ToolParam(description = "Swim Critical Swim Speed in seconds per 100m (e.g. 1:35/100m = 95). Null = unchanged.") Integer swimCssSecPer100m) {
        if (ftp == null && weightKg == null && thresholdPaceSecPerKm == null && swimCssSecPer100m == null) {
            throw new IllegalArgumentException("Provide at least one field to update (ftp, weightKg, thresholdPaceSecPerKm, swimCssSecPer100m).");
        }
        if (ftp != null && ftp <= 0) throw new IllegalArgumentException("FTP must be a positive integer.");
        if (weightKg != null && (weightKg <= 0 || weightKg > 300)) throw new IllegalArgumentException("weightKg must be 1-300.");
        if (thresholdPaceSecPerKm != null && thresholdPaceSecPerKm <= 0) throw new IllegalArgumentException("thresholdPaceSecPerKm must be positive.");
        if (swimCssSecPer100m != null && swimCssSecPer100m <= 0) throw new IllegalArgumentException("swimCssSecPer100m must be positive.");

        String userId = SecurityUtils.getCurrentUserId();
        User u = userService.getUserById(userId);
        return UserProfile.from(userService.updateSettings(userId,
                ftp != null ? ftp : u.getFtp(),
                weightKg != null ? weightKg : u.getWeightKg(),
                thresholdPaceSecPerKm != null ? thresholdPaceSecPerKm : u.getFunctionalThresholdPace(),
                swimCssSecPer100m != null ? swimCssSecPer100m : u.getCriticalSwimSpeed(),
                u.getPace5k(), u.getPace10k(), u.getPaceHalfMarathon(), u.getPaceMarathon(),
                u.getVo2maxPower(), u.getVo2maxPace(),
                u.getPower3MinW(), u.getPower12MinW(),
                u.getCustomZoneReferenceValues(), u.getAiPrePrompt(), u.getAiPrePromptEnabled()));
    }

    public record UserProfile(String id, String alias, String role,
                               Integer ftp, Integer weightKg,
                               Integer functionalThresholdPace, Integer criticalSwimSpeed,
                               Integer pace5k, Integer pace10k,
                               Double ctl, Double atl, Double tsb) {
        public static UserProfile from(User u) {
            return new UserProfile(
                    u.getId(), u.getAlias(),
                    u.getRole() != null ? u.getRole().name() : null,
                    u.getFtp(), u.getWeightKg(),
                    u.getFunctionalThresholdPace(), u.getCriticalSwimSpeed(),
                    u.getPace5k(), u.getPace10k(),
                    u.getCtl(), u.getAtl(), u.getTsb());
        }
    }
}
