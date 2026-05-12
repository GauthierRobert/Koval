package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.config.Provenance;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Free-form note authored by a coach about an athlete they manage. Optionally tied to a
 * specific completed session. Rendered as markdown in the coach view.
 */
@Getter
@Setter
@Document(collection = "coach_notes")
@CompoundIndexes({
        @CompoundIndex(name = "athleteId_createdAt_idx", def = "{'athleteId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "coachId_createdAt_idx", def = "{'coachId': 1, 'createdAt': -1}")
})
public class CoachNote {

    @Id
    private String id;

    private String coachId;
    private String athleteId;

    @Indexed
    private String sessionId;

    private String body;

    private Provenance provenance;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
