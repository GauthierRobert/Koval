package com.koval.trainingplannerbackend.club.group;

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
@Document(collection = "club_groups")
@CompoundIndex(name = "clubId_name", def = "{'clubId': 1, 'name': 1}", unique = true)
public class ClubGroup {
    @Id
    private String id;

    @Indexed
    private String clubId;

    private String name;
    private List<String> memberIds = new ArrayList<>();
    private LocalDateTime createdAt;
}
