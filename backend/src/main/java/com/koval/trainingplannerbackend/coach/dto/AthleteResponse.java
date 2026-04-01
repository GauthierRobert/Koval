package com.koval.trainingplannerbackend.coach.dto;

import com.koval.trainingplannerbackend.auth.User;

import java.util.List;
import java.util.Map;

public record AthleteResponse(
    String id,
    String displayName,
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
