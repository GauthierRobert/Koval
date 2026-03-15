package com.koval.trainingplannerbackend.club.dto;

public record LeaderboardEntry(String userId, String displayName, String profilePicture,
                               double weeklyTss, int sessionCount, int rank) {}
