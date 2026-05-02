package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class UserResponseMapper {

    private final GroupService groupService;

    public UserResponseMapper(GroupService groupService) {
        this.groupService = groupService;
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
        map.put("power3MinW", user.getPower3MinW());
        map.put("power12MinW", user.getPower12MinW());
        map.put("criticalPower", user.getCriticalPower());
        map.put("wPrimeJ", user.getWPrimeJ());

        map.put("hasCoach", groupService.athleteHasCoach(user.getId()));
        List<Group> userGroups = groupService.getGroupsForAthlete(user.getId());
        map.put("groups", userGroups.stream().map(Group::getName).toList());

        map.put("ctl", user.getCtl());
        map.put("atl", user.getAtl());
        map.put("tsb", user.getTsb());

        map.put("customZoneReferenceValues", user.getCustomZoneReferenceValues());
        map.put("needsOnboarding", user.getNeedsOnboarding());

        // CGU acceptance
        map.put("cguAcceptedAt", user.getCguAcceptedAt());
        map.put("cguVersion", user.getCguVersion());
        boolean needsCgu = user.getCguAcceptedAt() == null
                || !CguConstants.CURRENT_VERSION.equals(user.getCguVersion());
        map.put("needsCguAcceptance", needsCgu);

        Map<String, Boolean> linkedAccounts = new HashMap<>();
        linkedAccounts.put("strava", user.getStravaId() != null);
        linkedAccounts.put("google", user.getGoogleId() != null);
        linkedAccounts.put("garmin", user.getGarminUserId() != null);
        linkedAccounts.put("zwift", user.getZwiftUserId() != null);
        linkedAccounts.put("nolioRead", Boolean.TRUE.equals(user.getTerraProviderNolioConnected()));
        linkedAccounts.put("nolioWrite", user.getNolioAccessToken() != null);
        map.put("linkedAccounts", linkedAccounts);
        map.put("authProvider", Optional.ofNullable(user.getAuthProvider()).map(Enum::name).orElse(null));
        map.put("zwiftAutoSyncWorkouts", user.getZwiftAutoSyncWorkouts());
        map.put("nolioAutoSyncWorkouts", Boolean.TRUE.equals(user.getNolioAutoSyncWorkouts()));

        if (user.isCoach()) {
            List<String> athleteIds = groupService.getAthleteIdsForCoach(user.getId());
            map.put("athleteCount", athleteIds.size());
            map.put("aiPrePrompt", user.getAiPrePrompt());
            map.put("aiPrePromptEnabled", user.getAiPrePromptEnabled());
        }
        return map;
    }
}
