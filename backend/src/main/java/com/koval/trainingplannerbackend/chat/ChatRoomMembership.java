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
@Document(collection = "chat_room_memberships")
@CompoundIndexes({
    @CompoundIndex(name = "roomId_userId_uniq", def = "{'roomId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "userId_active", def = "{'userId': 1, 'active': 1}"),
    @CompoundIndex(name = "roomId_active", def = "{'roomId': 1, 'active': 1}")
})
public class ChatRoomMembership {

    @Id
    private String id;

    @Indexed
    private String roomId;

    @Indexed
    private String userId;

    /** Denormalized from the parent room for faster authorization short-circuits. Null for DIRECT rooms. */
    private String clubId;

    private Instant joinedAt;
    private Instant lastReadAt;
    private Boolean muted;

    private ChatMemberRole role;
    private MembershipSource source;

    /** Soft-leave: stays false when the user leaves so lastReadAt is preserved across rejoin. */
    private Boolean active;
}
