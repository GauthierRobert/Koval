package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.ClubVisibility;

public record CreateClubRequest(String name, String description, String location,
                                String logoUrl, ClubVisibility visibility) {}
