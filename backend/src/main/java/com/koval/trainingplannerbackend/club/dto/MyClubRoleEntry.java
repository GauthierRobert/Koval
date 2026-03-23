package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;

public record MyClubRoleEntry(String clubId, String clubName, ClubMemberRole role) {}
