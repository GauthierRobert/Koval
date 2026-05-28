package com.koval.trainingplannerbackend.ai.tools.club;

import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;

import java.util.List;

public record ClubMemberSummary(String userId, String alias,
                                String role, List<String> groupTags) {

    public static ClubMemberSummary from(ClubMemberResponse m) {
        return new ClubMemberSummary(m.userId(), m.alias(), m.role().name(), m.groups());
    }
}
