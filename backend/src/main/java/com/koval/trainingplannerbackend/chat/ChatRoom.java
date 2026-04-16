package com.koval.trainingplannerbackend.chat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "chat_rooms")
@CompoundIndexes({
    // One room per parent entity tuple. Works for every scope:
    //   CLUB              -> (CLUB, clubId, null)
    //   GROUP             -> (GROUP, clubId, groupId)
    //   OBJECTIVE         -> (OBJECTIVE, clubId, raceGroupingKey)
    //   RECURRING_SESSION -> (RECURRING_SESSION, clubId, templateId)
    //   SINGLE_SESSION    -> (SINGLE_SESSION, clubId, sessionId)
    //   DIRECT            -> (DIRECT, null, sortedUserIdsKey)
    @CompoundIndex(
        name = "scope_clubId_scopeRefId_uniq",
        def = "{'scope': 1, 'clubId': 1, 'scopeRefId': 1}",
        unique = true
    ),
    @CompoundIndex(name = "clubId_scope", def = "{'clubId': 1, 'scope': 1}")
})
public class ChatRoom {

    @Id
    private String id;

    @Indexed
    private ChatRoomScope scope;

    /** Club this room belongs to. Null only for DIRECT rooms. */
    private String clubId;

    /**
     * Parent entity id within the club. Semantics per scope:
     *  - CLUB: null
     *  - GROUP: ClubGroup.id
     *  - OBJECTIVE: raceId or derived "title|date" key matching ClubStatsService grouping
     *  - RECURRING_SESSION: RecurringSessionTemplate.id
     *  - SINGLE_SESSION: ClubTrainingSession.id
     *  - DIRECT: sorted "userA|userB" key that dedupes across both directions
     */
    private String scopeRefId;

    /** Denormalized display name, kept in sync by the hook-point services. */
    private String title;

    /** True for OBJECTIVE rooms — any active club member can call join. */
    private Boolean joinable;

    private Instant lastMessageAt;
    private String lastMessagePreview;
    private String lastMessageSenderId;

    private Instant createdAt;
    private String createdBy;
    private Boolean archived;
}
