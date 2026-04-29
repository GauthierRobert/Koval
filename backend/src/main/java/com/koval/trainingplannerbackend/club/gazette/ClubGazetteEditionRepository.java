package com.koval.trainingplannerbackend.club.gazette;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClubGazetteEditionRepository extends MongoRepository<ClubGazetteEdition, String> {

    List<ClubGazetteEdition> findByClubIdAndStatusOrderByPeriodStartDesc(
            String clubId, GazetteStatus status);

    List<ClubGazetteEdition> findByClubIdOrderByPeriodStartDesc(String clubId, Pageable pageable);

    Optional<ClubGazetteEdition> findByClubIdAndEditionNumber(String clubId, int editionNumber);

    Optional<ClubGazetteEdition> findFirstByClubIdAndStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
            String clubId, GazetteStatus status, LocalDateTime atStart, LocalDateTime atEnd);

    int countByClubId(String clubId);
}
