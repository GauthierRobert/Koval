package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.ClubMemberRole;

import java.time.LocalDateTime;
import java.util.List;

public record ClubMemberResponse(String membershipId, String userId, String displayName,
                                 String profilePicture, ClubMemberRole role,
                                 LocalDateTime joinedAt, List<String> tags) {}
