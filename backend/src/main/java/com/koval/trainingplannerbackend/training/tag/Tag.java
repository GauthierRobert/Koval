package com.koval.trainingplannerbackend.training.tag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "tags")
@CompoundIndex(name = "name_coachId", def = "{'name': 1, 'coachId': 1}", unique = true)
public class Tag {
    @Id
    private String id;

    private String name;
    private String coachId;

    @Indexed
    private List<String> athleteIds = new ArrayList<>();

    private LocalDateTime createdAt;

    public Tag() {
        this.createdAt = LocalDateTime.now();
    }
}
