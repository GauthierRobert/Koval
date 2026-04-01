package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.ClubVisibility;

public record ClubSummaryResponse(String id, String name, String description, String logoUrl,
                                  ClubVisibility visibility, String membershipStatus) {}
