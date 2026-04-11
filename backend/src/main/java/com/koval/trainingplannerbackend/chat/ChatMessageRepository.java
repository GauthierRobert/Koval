package com.koval.trainingplannerbackend.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    /** First page: latest messages, sorted descending. */
    List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

    /** Keyset pagination: messages strictly older than {@code before}, latest first. */
    List<ChatMessage> findByRoomIdAndCreatedAtLessThanOrderByCreatedAtDesc(String roomId, Instant before, Pageable pageable);

    /** Unread count for a single room (all messages strictly after lastReadAt). */
    long countByRoomIdAndCreatedAtGreaterThan(String roomId, Instant after);

    Optional<ChatMessage> findFirstByRoomIdAndClientNonce(String roomId, String clientNonce);
}
