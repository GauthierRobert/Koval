package com.koval.trainingplannerbackend.club.tools;

import com.koval.trainingplannerbackend.club.group.ClubGroup;

public record ClubGroupSummary(String id, String name, int memberCount) {

    public static ClubGroupSummary from(ClubGroup g) {
        return new ClubGroupSummary(g.getId(), g.getName(), g.getMemberIds().size());
    }
}
