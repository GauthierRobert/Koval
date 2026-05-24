package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    public static final String USER_ID_KEY = "userId";

    private SecurityUtils() {}

    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return (String) auth.getPrincipal();
    }

    /**
     * Enforce that the authenticated caller holds the given role. The JWT filter stamps
     * authorities as {@code ROLE_<UserRole>}. Throws {@link ForbiddenOperationException}
     * (surfaced to MCP clients as an error tool result) when the role is missing.
     */
    public static void requireRole(UserRole role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasRole = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role.name()).equals(a.getAuthority()));
        if (!hasRole) {
            throw new ForbiddenOperationException("This action requires the " + role + " role.");
        }
    }

    public static void requireCoach() {
        requireRole(UserRole.COACH);
    }

    /** Extract userId from ToolContext (set server-side, invisible to the AI model). */
    public static String getUserId(ToolContext context) {
        if (context != null) {
            Object id = context.getContext().get(USER_ID_KEY);
            if (id instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return getCurrentUserId();
    }
}
