package com.koval.trainingplannerbackend.race;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RaceRepository extends MongoRepository<Race, String> {

    List<Race> findByTitleContainingIgnoreCase(String query);

    List<Race> findBySportIgnoreCase(String sport);

    List<Race> findByTitleContainingIgnoreCaseAndSportIgnoreCase(String query, String sport);

    List<Race> findByCountryIgnoreCaseOrRegionIgnoreCase(String country, String region);

    Page<Race> findBySportIgnoreCaseAndCountryIgnoreCase(String sport, String country, Pageable pageable);
}
