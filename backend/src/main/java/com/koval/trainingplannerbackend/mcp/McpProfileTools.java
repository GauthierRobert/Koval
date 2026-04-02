package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.ai.tool.annotation.Tool;
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
