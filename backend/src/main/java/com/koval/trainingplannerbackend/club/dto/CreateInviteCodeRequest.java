package com.koval.trainingplannerbackend.club.dto;

public record CreateInviteCodeRequest(String clubGroupId, int maxUses, String expiresAt) {}
