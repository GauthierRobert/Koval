package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Club member summary returned over REST to the in-app UI. Carries both the real
 * {@code displayName} (shown above the alias so members recognise each other) and the
 * anonymous {@code alias}. MCP responses use a separate alias-only DTO so real names
 * never cross that boundary — see {@code McpClubTools}.
 */
public record ClubMemberResponse(String membershipId, String userId,
                                 String displayName, String alias, String profilePicture,
                                 ClubMemberRole role,
                                 LocalDateTime joinedAt, List<String> groups) {}
