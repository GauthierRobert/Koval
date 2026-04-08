package com.koval.trainingplannerbackend.ai;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatHistoryRepository extends MongoRepository<ChatHistory, String> {

    List<ChatHistory> findByUserIdOrderByLastUpdatedAtDesc(String userId);

    long deleteByLastUpdatedAtBefore(LocalDateTime cutoff);
}
