package com.koval.trainingplannerbackend.chat;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMembershipRepository extends MongoRepository<ChatRoomMembership, String> {

    Optional<ChatRoomMembership> findByRoomIdAndUserId(String roomId, String userId);

    List<ChatRoomMembership> findByUserIdAndActiveTrue(String userId);

    List<ChatRoomMembership> findByRoomIdAndActiveTrue(String roomId);

    List<ChatRoomMembership> findByRoomId(String roomId);

    List<ChatRoomMembership> findByClubIdAndUserId(String clubId, String userId);
}
