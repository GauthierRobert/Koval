package com.koval.trainingplannerbackend.club.recurring;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecurringSessionTemplateRepository extends MongoRepository<RecurringSessionTemplate, String> {
    List<RecurringSessionTemplate> findByClubId(String clubId);
    List<RecurringSessionTemplate> findByClubIdAndActiveTrue(String clubId);
    List<RecurringSessionTemplate> findByActiveTrue();
}
