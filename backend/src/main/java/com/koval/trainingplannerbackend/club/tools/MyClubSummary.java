package com.koval.trainingplannerbackend.club.tools;

import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;

public record MyClubSummary(String id, String name, String description,
                            int memberCount, String membershipStatus) {

    public static MyClubSummary from(ClubSummaryResponse c) {
        return new MyClubSummary(c.id(), c.name(), c.description(), c.memberCount(), c.membershipStatus());
    }
}
