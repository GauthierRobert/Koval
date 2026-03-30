package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.ClubVisibility;
import jakarta.validation.constraints.NotBlank;

public record CreateClubRequest(@NotBlank String name, String description, String location,
                                String logoUrl, ClubVisibility visibility) {}
