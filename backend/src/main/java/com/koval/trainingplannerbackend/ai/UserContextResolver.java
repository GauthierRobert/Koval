package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;
import com.koval.trainingplannerbackend.club.group.ClubGroupService;
import com.koval.trainingplannerbackend.coach.CoachService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves user context (profile, athletes, clubs) for AI prompt injection.
 * Pre-fetches role-specific data so agents don't need tool calls for basic info.
 */
@Component
public class UserContextResolver {

    private static final Logger log = LoggerFactory.getLogger(UserContextResolver.class);

    public static final String COACH_ROLE = "COACH";

    private static final String DEFAULT_ROLE = "ATHLETE";
    private static final int DEFAULT_FTP = 250;

    private final UserRepository userRepository;
    private final CoachService coachService;
    private final ClubService clubService;
    private final ClubGroupService clubGroupService;

    public UserContextResolver(UserRepository userRepository,
                               CoachService coachService,
                               ClubService clubService,
                               ClubGroupService clubGroupService) {
        this.userRepository = userRepository;
        this.coachService = coachService;
        this.clubService = clubService;
        this.clubGroupService = clubGroupService;
    }

    public UserContext resolve(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new UserContext(userId, DEFAULT_ROLE, DEFAULT_FTP, null,
                    null, null, null, null, null, null,
                    List.of(), List.of(), List.of());
        }

        String role = user.getRole().name();
        int ftp = user.getFtp() != null ? user.getFtp() : DEFAULT_FTP;
        String aiPrePrompt = user.isAiPrePromptEnabled() ? user.getAiPrePrompt() : null;

        // Full profile fields
        String displayName = user.getDisplayName();
        Integer css = user.getCriticalSwimSpeed();
        Integer ftPace = user.getFunctionalThresholdPace();
        Double ctl = user.getCtl();
        Double atl = user.getAtl();
        Double tsb = user.getTsb();

        // Coach-specific context
        List<AthleteSummary> athletes = List.of();
        List<GroupSummary> athleteGroups = List.of();
        List<ClubContext> clubs = List.of();

        if (COACH_ROLE.equals(role)) {
            athletes = resolveAthletes(userId);
            athleteGroups = resolveGroups(userId);
            clubs = resolveClubs(userId);
        }

        return new UserContext(userId, role, ftp, aiPrePrompt,
                displayName, css, ftPace, ctl, atl, tsb,
                athletes, athleteGroups, clubs);
    }

    private List<AthleteSummary> resolveAthletes(String coachId) {
        try {
            return coachService.getCoachAthletes(coachId).stream()
                    .map(u -> new AthleteSummary(u.getId(), u.getDisplayName()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to resolve athletes for coach {}: {}", coachId, e.getMessage());
            return List.of();
        }
    }

    private List<GroupSummary> resolveGroups(String coachId) {
        try {
            return coachService.getAthleteGroupsForCoach(coachId).stream()
                    .map(g -> new GroupSummary(g.getId(), g.getName()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to resolve groups for coach {}: {}", coachId, e.getMessage());
            return List.of();
        }
    }

    private List<ClubContext> resolveClubs(String userId) {
        try {
            List<ClubSummaryResponse> userClubs = clubService.getUserClubs(userId);
            return userClubs.stream().map(c -> {
                List<GroupSummary> groups;
                try {
                    groups = clubGroupService.listGroups(userId, c.id()).stream()
                            .map(g -> new GroupSummary(g.getId(), g.getName()))
                            .toList();
                } catch (Exception e) {
                    groups = List.of();
                }
                return new ClubContext(c.id(), c.name(), groups);
            }).toList();
        } catch (Exception e) {
            log.warn("Failed to resolve clubs for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ── Records ─────────────────────────────────────────────────────────

    public record UserContext(
            String userId, String role, int ftp, String aiPrePrompt,
            String displayName, Integer css, Integer ftPace, Double ctl, Double atl, Double tsb,
            List<AthleteSummary> athletes,
            List<GroupSummary> athleteGroups,
            List<ClubContext> clubs
    ) {}

    public record AthleteSummary(String id, String displayName) {}
    public record GroupSummary(String id, String name) {}
    public record ClubContext(String id, String name, List<GroupSummary> groups) {}
}
