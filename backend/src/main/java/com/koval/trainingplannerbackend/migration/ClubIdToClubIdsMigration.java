package com.koval.trainingplannerbackend.migration;

import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class ClubIdToClubIdsMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClubIdToClubIdsMigration.class);

    private final MongoTemplate mongoTemplate;

    public ClubIdToClubIdsMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        // 1. Convert documents where clubId is a string → clubIds: [value], unset clubId
        Query oldFormat = new Query(Criteria.where("clubId").exists(true).ne(null));
        long count = mongoTemplate.count(oldFormat, "trainings");
        if (count > 0) {
            log.info("Migrating {} training(s) from clubId to clubIds...", count);
            mongoTemplate.getCollection("trainings").find(
                    com.mongodb.client.model.Filters.and(
                            com.mongodb.client.model.Filters.exists("clubId", true),
                            com.mongodb.client.model.Filters.ne("clubId", null),
                            com.mongodb.client.model.Filters.not(
                                    com.mongodb.client.model.Filters.type("clubId", org.bson.BsonType.ARRAY)
                            )
                    )
            ).forEach(doc -> {
                Object clubId = doc.get("clubId");
                if (clubId instanceof String cid && !cid.isBlank()) {
                    mongoTemplate.getCollection("trainings").updateOne(
                            com.mongodb.client.model.Filters.eq("_id", doc.get("_id")),
                            com.mongodb.client.model.Updates.combine(
                                    com.mongodb.client.model.Updates.set("clubIds", java.util.List.of(cid)),
                                    com.mongodb.client.model.Updates.unset("clubId")
                            )
                    );
                } else {
                    mongoTemplate.getCollection("trainings").updateOne(
                            com.mongodb.client.model.Filters.eq("_id", doc.get("_id")),
                            com.mongodb.client.model.Updates.combine(
                                    com.mongodb.client.model.Updates.set("clubIds", java.util.List.of()),
                                    com.mongodb.client.model.Updates.unset("clubId")
                            )
                    );
                }
            });
            log.info("Migration complete: clubId → clubIds");
        } else {
            log.info("No trainings with old clubId format found — skipping migration.");
        }

        // 2. Ensure all documents without clubIds get an empty array
        Query missingClubIds = new Query(Criteria.where("clubIds").exists(false));
        UpdateResult result = mongoTemplate.updateMulti(missingClubIds,
                new Update().set("clubIds", java.util.List.of()), "trainings");
        if (result.getModifiedCount() > 0) {
            log.info("Set empty clubIds on {} training(s).", result.getModifiedCount());
        }
    }
}
