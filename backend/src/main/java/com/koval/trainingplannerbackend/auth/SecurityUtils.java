package com.koval.trainingplannerbackend.auth;

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
