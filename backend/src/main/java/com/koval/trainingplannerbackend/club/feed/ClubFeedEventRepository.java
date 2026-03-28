package com.koval.trainingplannerbackend.club.feed;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClubFeedEventRepository extends MongoRepository<ClubFeedEvent, String> {

    List<ClubFeedEvent> findByClubIdOrderByCreatedAtDesc(String clubId, Pageable pageable);

    List<ClubFeedEvent> findByClubIdAndPinnedTrueOrderByCreatedAtDesc(String clubId);

    Optional<ClubFeedEvent> findByClubSessionIdAndType(String clubSessionId, ClubFeedEventType type);

    Optional<ClubFeedEvent> findFirstByClubIdAndTypeAndPinnedTrueOrderByCreatedAtDesc(
            String clubId, ClubFeedEventType type);

    List<ClubFeedEvent> findByClubIdAndTypeOrderByCreatedAtDesc(
            String clubId, ClubFeedEventType type, Pageable pageable);
}
