package com.koval.trainingplannerbackend.integration.nolio.write;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Issues the {@code id_partner} values Nolio uses to key our planned trainings.
 * Nolio requires a partner-chosen integer that identifies the workout on their
 * side for update/delete, so values must never collide — an atomic Mongo
 * counter guarantees that where a hash of the training id could not.
 */
@Component
public class NolioPartnerIdGenerator {

    private static final String COUNTERS_COLLECTION = "counters";
    private static final String COUNTER_ID = "nolio_partner_id";

    private final MongoTemplate mongoTemplate;

    public NolioPartnerIdGenerator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long next() {
        Query query = Query.query(Criteria.where("_id").is(COUNTER_ID));
        Update update = new Update().inc("seq", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
        Document counter = mongoTemplate.findAndModify(query, update, options, Document.class, COUNTERS_COLLECTION);
        if (counter == null) {
            throw new IllegalStateException("Failed to increment Nolio partner id counter");
        }
        return ((Number) counter.get("seq")).longValue();
    }
}
