package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;

public record AthleteSummary(
        String id,
        String displayName,
        UserRole role,
        Integer ftp
) {
    public static AthleteSummary from(User u) {
        return new AthleteSummary(
                u.getId(),
                u.getDisplayName(),
                u.getRole(),
                u.getFtp()
        );
    }
}
