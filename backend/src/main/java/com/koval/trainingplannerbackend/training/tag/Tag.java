package com.koval.trainingplannerbackend.training.tag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "tags")
public class Tag {
    @Id
    private String id;

    @Indexed(unique = true)
    private String name; // lowercase, normalized

    private String displayName;
    private TagVisibility visibility = TagVisibility.PUBLIC;
    private String createdBy;
    private LocalDateTime createdAt;

    public Tag() {
        this.createdAt = LocalDateTime.now();
    }
}
