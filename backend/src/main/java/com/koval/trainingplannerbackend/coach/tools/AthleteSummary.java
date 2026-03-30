package com.koval.trainingplannerbackend.coach.tools;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;

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
