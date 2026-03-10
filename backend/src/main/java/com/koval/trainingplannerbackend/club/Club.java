package com.koval.trainingplannerbackend.club;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "clubs")
public class Club {
    @Id
    private String id;

    @Indexed
    private String name;

    private String description;
    private String location;
    private String logoUrl;
    private ClubVisibility visibility;

    @Indexed
    private String ownerId;

    private int memberCount;
    private LocalDateTime createdAt;
}
