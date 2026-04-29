package com.koval.trainingplannerbackend.media;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MediaRepository extends MongoRepository<Media, String> {

    List<Media> findByConfirmedFalseAndCreatedAtBefore(LocalDateTime cutoff);

    long countByOwnerIdAndConfirmedFalse(String ownerId);
}
