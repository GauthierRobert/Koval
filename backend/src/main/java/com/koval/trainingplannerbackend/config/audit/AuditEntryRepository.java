package com.koval.trainingplannerbackend.config.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEntryRepository extends MongoRepository<AuditEntry, String> {

    Page<AuditEntry> findByActorUserIdOrderByTimestampDesc(String actorUserId, Pageable pageable);

    Page<AuditEntry> findByActionOrderByTimestampDesc(String action, Pageable pageable);
}
