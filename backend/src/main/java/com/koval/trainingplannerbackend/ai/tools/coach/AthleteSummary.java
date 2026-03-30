package com.koval.trainingplannerbackend.ai.tools.coach;

import com.koval.trainingplannerbackend.auth.User;

public record AthleteSummary(
        String id,
        String displayName
) {
    public static AthleteSummary from(User u) {
        return new AthleteSummary(
                u.getId(),
                u.getDisplayName()
        );
    }
}
