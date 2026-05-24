package com.koval.trainingplannerbackend.context;

import com.koval.trainingplannerbackend.config.Provenance;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A coach's general coaching philosophy — how they prefer to train athletes (periodization,
 * intensity distribution, signature sessions, voice). One document per coach (upserted).
 * Surfaced to the coach themselves and folded into {@code getAthleteContext} so the LLM knows
 * how this coach coaches before reasoning about a specific athlete. Content is a section-title
 * → markdown map matching the coach onboarding template.
 */
@Getter
@Setter
@Document(collection = "coach_context")
public class CoachContext {

    @Id
    private String id;

    @Indexed(unique = true)
    private String coachId;

    private Map<String, String> sections = new LinkedHashMap<>();

    private Provenance provenance;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
