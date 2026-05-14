package com.koval.trainingplannerbackend.ai.agents;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum AgentType {
    TRAINING_CREATION,
    SCHEDULING,
    ANALYSIS,
    COACH_MANAGEMENT,
    CLUB_MANAGEMENT,
    GENERAL;

    private static final Map<String, AgentType> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    /** Safe lookup that avoids the IllegalArgumentException thrown by {@link #valueOf}. */
    public static Optional<AgentType> parse(String name) {
        return Optional.ofNullable(name)
                .map(String::trim)
                .map(s -> s.toUpperCase(java.util.Locale.ROOT))
                .map(BY_NAME::get);
    }
}
