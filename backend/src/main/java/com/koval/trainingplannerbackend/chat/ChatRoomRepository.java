package com.koval.trainingplannerbackend.chat;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {

    /**
     * Look up a room by its parent entity. Semantics of {@code clubId} and {@code scopeRefId}
     * match the {@link ChatRoom} field documentation, including {@code null} where applicable:
     *   CLUB   -> (CLUB, clubId, null)
     *   DIRECT -> (DIRECT, null, sortedUserIdsKey)
     */
    Optional<ChatRoom> findByScopeAndClubIdAndScopeRefId(ChatRoomScope scope, String clubId, String scopeRefId);

    List<ChatRoom> findByClubId(String clubId);
}
