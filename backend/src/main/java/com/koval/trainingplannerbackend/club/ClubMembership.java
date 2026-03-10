package com.koval.trainingplannerbackend.club;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "club_memberships")
public class ClubMembership {
    @Id
    private String id;

    @Indexed
    private String clubId;

    @Indexed
    private String userId;

    private ClubMemberRole role;
    private ClubMemberStatus status;
    private LocalDateTime joinedAt;
    private LocalDateTime requestedAt;
}
