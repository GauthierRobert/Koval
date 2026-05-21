package com.koval.trainingplannerbackend.integration.sync;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutSyncRecordRepository extends MongoRepository<WorkoutSyncRecord, String> {

    Optional<WorkoutSyncRecord> findByAthleteIdAndSourceTypeAndSourceIdAndProviderId(
            String athleteId, WorkoutSyncSourceType sourceType, String sourceId, String providerId);

    List<WorkoutSyncRecord> findByAthleteIdAndSourceTypeAndSourceId(
            String athleteId, WorkoutSyncSourceType sourceType, String sourceId);

    List<WorkoutSyncRecord> findBySourceTypeAndSourceId(
            WorkoutSyncSourceType sourceType, String sourceId);

    void deleteByAthleteIdAndSourceTypeAndSourceId(
            String athleteId, WorkoutSyncSourceType sourceType, String sourceId);
}
