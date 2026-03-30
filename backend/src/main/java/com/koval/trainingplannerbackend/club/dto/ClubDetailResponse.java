package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.ClubVisibility;
import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;

import java.time.LocalDateTime;

public record ClubDetailResponse(String id, String name, String description, String location,
                                 String logoUrl, ClubVisibility visibility, int memberCount,
                                 String ownerId, String currentMembershipStatus,
                                 ClubMemberRole currentMemberRole, LocalDateTime createdAt) {}
