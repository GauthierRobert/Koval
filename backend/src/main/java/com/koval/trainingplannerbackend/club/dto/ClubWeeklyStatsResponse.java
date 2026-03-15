package com.koval.trainingplannerbackend.club.dto;

public record ClubWeeklyStatsResponse(double totalSwimKm, double totalBikeKm, double totalRunKm,
                                      int totalSessions, int memberCount) {}
