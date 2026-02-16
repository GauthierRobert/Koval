package com.koval.trainingplannerbackend.training.zone;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneSystemRepository extends MongoRepository<ZoneSystem, String> {
    List<ZoneSystem> findByCoachId(String coachId);

    List<ZoneSystem> findByCoachIdAndSportType(String coachId,
            com.koval.trainingplannerbackend.training.SportType sportType);

    Optional<ZoneSystem> findByCoachIdAndSportTypeAndIsActiveTrue(String coachId,
            com.koval.trainingplannerbackend.training.SportType sportType);

    Optional<ZoneSystem> findByCoachIdAndSportTypeAndIsDefaultTrue(String coachId,
            com.koval.trainingplannerbackend.training.SportType sportType);

    // Deprecated or keep for backward compat (assumes Cycling if not specified? Or
    // returns all?)
    Optional<ZoneSystem> findByCoachIdAndIsActiveTrue(String coachId);

    Optional<ZoneSystem> findByCoachIdAndIsDefaultTrue(String coachId);
}
