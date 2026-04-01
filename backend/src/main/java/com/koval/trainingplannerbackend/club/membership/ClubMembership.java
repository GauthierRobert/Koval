package com.koval.trainingplannerbackend.club.membership;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "club_memberships")
@CompoundIndexes({
    @CompoundIndex(name = "clubId_status", def = "{'clubId': 1, 'status': 1}"),
    @CompoundIndex(name = "clubId_userId", def = "{'clubId': 1, 'userId': 1}", unique = true)
})
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
