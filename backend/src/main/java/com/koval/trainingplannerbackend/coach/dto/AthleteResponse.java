package com.koval.trainingplannerbackend.coach.dto;

import com.koval.trainingplannerbackend.auth.User;

import java.util.List;
import java.util.Map;

/**
 * Coach-facing athlete summary returned over REST to the coach's own UI. Carries both the
 * real {@code displayName} (rendered above the alias so the coach recognises the person)
 * and the anonymous {@code alias} (rendered below, and the only handle they should use when
 * referring to the athlete with any AI client). MCP responses use a separate alias-only DTO
 * so the real name never crosses that boundary — see {@code McpCoachTools.AthleteSummary}.
 */
public record AthleteResponse(
    String id,
    String displayName,
    String alias,
    String profilePicture,
    String role,
    Integer ftp,
    Integer weightKg,
    Integer functionalThresholdPace,
    Integer criticalSwimSpeed,
    Integer pace5k,
    Integer pace10k,
    Integer paceHalfMarathon,
    Integer paceMarathon,
    Integer vo2maxPower,
    Integer vo2maxPace,
    Map<String, Integer> customZoneReferenceValues,
    List<String> groups,
    List<String> clubs,
    boolean hasCoach
) {
    public static AthleteResponse from(User athlete, List<String> groups, List<String> clubs, boolean hasCoach) {
        return new AthleteResponse(
                athlete.getId(),
                athlete.getDisplayName(),
                athlete.getAlias(),
                athlete.getProfilePicture(),
                athlete.getRole().name(),
                athlete.getFtp(),
                athlete.getWeightKg(),
                athlete.getFunctionalThresholdPace(),
                athlete.getCriticalSwimSpeed(),
                athlete.getPace5k(),
                athlete.getPace10k(),
                athlete.getPaceHalfMarathon(),
                athlete.getPaceMarathon(),
                athlete.getVo2maxPower(),
                athlete.getVo2maxPace(),
                athlete.getCustomZoneReferenceValues(),
                groups,
                clubs,
                hasCoach
        );
    }
}
