package com.koval.trainingplannerbackend.club.tools;

import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;

import java.util.List;

public record ClubMemberSummary(String userId, String displayName,
                                String role, List<String> groupTags) {

    public static ClubMemberSummary from(ClubMemberResponse m) {
        return new ClubMemberSummary(m.userId(), m.displayName(), m.role().name(), m.tags());
    }
}
