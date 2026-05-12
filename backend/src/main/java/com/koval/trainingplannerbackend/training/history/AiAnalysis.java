package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.config.Provenance;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI-generated analysis attached to a {@link CompletedSession}. Authored by the session
 * owner or by a coach managing the athlete. The body is markdown rendered in the session
 * detail UI.
 */
@Getter
@Setter
@Document(collection = "ai_analyses")
@CompoundIndexes({
        @CompoundIndex(name = "sessionId_createdAt_idx", def = "{'sessionId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "athleteId_createdAt_idx", def = "{'athleteId': 1, 'createdAt': -1}")
})
public class AiAnalysis {

    @Id
    private String id;

    private String sessionId;
    private String athleteId;
    private String authorId;

    private String summary;
    private String body;
    private List<String> highlights;

    private Provenance provenance;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
