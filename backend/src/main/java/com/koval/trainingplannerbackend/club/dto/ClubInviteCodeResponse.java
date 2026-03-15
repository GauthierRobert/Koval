package com.koval.trainingplannerbackend.club.dto;

public record ClubInviteCodeResponse(String id, String code, String clubId, String createdBy,
                                     String clubGroupId, String clubGroupName,
                                     int maxUses, int currentUses,
                                     String expiresAt, boolean active, String createdAt) {}
