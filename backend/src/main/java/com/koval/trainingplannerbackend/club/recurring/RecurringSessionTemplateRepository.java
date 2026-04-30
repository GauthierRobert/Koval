package com.koval.trainingplannerbackend.club.recurring;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface RecurringSessionTemplateRepository extends MongoRepository<RecurringSessionTemplate, String> {
    List<RecurringSessionTemplate> findByClubId(String clubId);
    List<RecurringSessionTemplate> findByClubIdAndActiveTrue(String clubId);
    List<RecurringSessionTemplate> findByActiveTrue();

    @Query("{ 'clubId': ?0, 'active': true, $or: [ { 'endDate': null }, { 'endDate': { $gte: ?1 } } ] }")
    List<RecurringSessionTemplate> findActiveAndNotExpiredForClub(String clubId, LocalDate today);

    @Query("{ 'clubId': { $in: ?0 }, 'active': true, $or: [ { 'endDate': null }, { 'endDate': { $gte: ?1 } } ] }")
    List<RecurringSessionTemplate> findActiveAndNotExpiredForClubs(List<String> clubIds, LocalDate today);
}
