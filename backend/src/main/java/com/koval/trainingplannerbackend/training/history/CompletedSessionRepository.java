package com.koval.trainingplannerbackend.training.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link CompletedSession} documents, including projection queries for external activity IDs. */
public interface CompletedSessionRepository extends MongoRepository<CompletedSession, String> {

    @Query(value = "{ 'userId': ?0, 'stravaActivityId': { $ne: null } }", fields = "{ 'stravaActivityId': 1 }")
    List<CompletedSession> findStravaActivityIdsByUserId(String userId);

    @Query(value = "{ 'userId': ?0, 'garminActivityId': { $ne: null } }", fields = "{ 'garminActivityId': 1 }")
    List<CompletedSession> findGarminActivityIdsByUserId(String userId);

    @Query(value = "{ 'userId': ?0, 'zwiftActivityId': { $ne: null } }", fields = "{ 'zwiftActivityId': 1 }")
    List<CompletedSession> findZwiftActivityIdsByUserId(String userId);

    @Query(value = "{ 'userId': ?0, 'nolioActivityId': { $ne: null } }", fields = "{ 'nolioActivityId': 1 }")
    List<CompletedSession> findNolioActivityIdsByUserId(String userId);

    @Query(value = "{ 'userId': ?0, 'polarActivityId': { $ne: null } }", fields = "{ 'polarActivityId': 1 }")
    List<CompletedSession> findPolarActivityIdsByUserId(String userId);

    Optional<CompletedSession> findByUserIdAndNolioActivityId(String userId, String nolioActivityId);

    List<CompletedSession> findByUserIdOrderByCompletedAtDesc(String userId);
    Page<CompletedSession> findByUserIdOrderByCompletedAtDesc(String userId, Pageable pageable);
    List<CompletedSession> findByUserIdOrderByCompletedAtAsc(String userId);
    List<CompletedSession> findByUserIdAndCompletedAtGreaterThanEqualOrderByCompletedAtAsc(
            String userId, LocalDateTime from);
    List<CompletedSession> findByUserIdAndCompletedAtBetween(
            String userId, LocalDateTime from, LocalDateTime to);
    Optional<CompletedSession> findByIdAndUserId(String id, String userId);
    Optional<CompletedSession> findByScheduledWorkoutId(String scheduledWorkoutId);
    List<CompletedSession> findByClubSessionId(String clubSessionId);
    List<CompletedSession> findByUserIdInAndCompletedAtBetween(
            List<String> userIds, LocalDateTime from, LocalDateTime to);

    List<CompletedSession> findByUserIdAndGroupId(String userId, String groupId);

    /** Sessions the user classified against a race day with the given role (e.g. RACE). */
    List<CompletedSession> findByUserIdAndRaceRole(String userId, RaceRole raceRole);

    /**
     * Sessions with a GridFS FIT pointer but no GCS object yet — i.e. waiting to
     * be backfilled into Cloud Storage. Used by the one-shot migration runner.
     */
    @Query(value = "{ 'fitFileId': { $ne: null }, $or: [ { 'fitGcsObject': null }, { 'fitGcsObject': { $exists: false } } ] }",
            fields = "{ '_id': 1, 'userId': 1, 'fitFileId': 1, 'fitGcsObject': 1 }")
    Page<CompletedSession> findFitMigrationCandidates(Pageable pageable);
}
