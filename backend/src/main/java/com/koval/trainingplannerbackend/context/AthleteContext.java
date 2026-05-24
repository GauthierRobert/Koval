package com.koval.trainingplannerbackend.context;

import com.koval.trainingplannerbackend.config.Provenance;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Free-form, structured context about an athlete, keyed by the athlete it describes and the
 * author who wrote it. Two flavours coexist in this collection:
 * <ul>
 *   <li>{@code authorRole=ATHLETE}, {@code authorId == athleteId} — the athlete's self-context
 *       (habits, availability, how they want to be trained).</li>
 *   <li>{@code authorRole=COACH}, {@code authorId == coachId} — a coach's private context about
 *       the athlete. Never returned to the athlete.</li>
 * </ul>
 * Each (athleteId, authorId) pair owns at most one document (upserted), enforced by a unique
 * compound index. Content is a section-title → markdown map matching the onboarding template.
 */
@Getter
@Setter
@Document(collection = "athlete_context")
@CompoundIndexes({
        @CompoundIndex(name = "athleteId_authorId_idx", def = "{'athleteId': 1, 'authorId': 1}",
                unique = true)
})
public class AthleteContext {

    @Id
    private String id;

    @Indexed
    private String athleteId;

    private String authorId;

    private ContextAuthorRole authorRole;

    private Map<String, String> sections = new LinkedHashMap<>();

    private Provenance provenance;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
