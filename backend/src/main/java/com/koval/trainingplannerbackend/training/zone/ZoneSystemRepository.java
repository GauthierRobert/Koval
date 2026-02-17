package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneSystemRepository extends MongoRepository<ZoneSystem, String> {
    List<ZoneSystem> findByCoachId(String coachId);

    List<ZoneSystem> findByCoachIdAndSportType(String coachId,
                                               SportType sportType);

    Optional<ZoneSystem> findByCoachIdAndName(String coachId, String name);
}
