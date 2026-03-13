package com.koval.trainingplannerbackend.club;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ClubTrainingSessionRepository extends MongoRepository<ClubTrainingSession, String> {
    List<ClubTrainingSession> findByClubIdOrderByScheduledAtDesc(String clubId);
    List<ClubTrainingSession> findByRecurringTemplateIdAndScheduledAtBetween(String recurringTemplateId, LocalDateTime start, LocalDateTime end);
    List<ClubTrainingSession> findByClubIdAndScheduledAtBetween(String clubId, LocalDateTime start, LocalDateTime end);
    List<ClubTrainingSession> findByClubIdInAndScheduledAtBetween(List<String> clubIds, LocalDateTime start, LocalDateTime end);
    List<ClubTrainingSession> findByResponsibleCoachIdAndLinkedTrainingIdIsNullAndScheduledAtBetween(
            String responsibleCoachId, LocalDateTime from, LocalDateTime to);
}
