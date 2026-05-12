package com.koval.trainingplannerbackend.coach;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CoachNoteRepository extends MongoRepository<CoachNote, String> {

    List<CoachNote> findByAthleteIdOrderByCreatedAtDesc(String athleteId, Pageable pageable);

    List<CoachNote> findByAthleteIdAndSessionIdOrderByCreatedAtDesc(String athleteId, String sessionId);
}
