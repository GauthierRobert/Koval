package com.koval.trainingplannerbackend.club;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "club_activities")
public class ClubActivity {
    @Id
    private String id;

    @Indexed
    private String clubId;

    private ClubActivityType type;
    private String actorId;
    private String targetId;
    private String targetTitle;
    private LocalDateTime occurredAt;
}
