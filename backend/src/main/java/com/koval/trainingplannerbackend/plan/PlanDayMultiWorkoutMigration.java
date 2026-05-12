package com.koval.trainingplannerbackend.plan;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts legacy single-workout PlanDay docs to multi-workout shape:
 *   {trainingId: "x", scheduledWorkoutId: "y"}
 * becomes
 *   {trainingIds: ["x"], scheduledWorkoutIds: ["y"]}
 *
 * Idempotent: skips plans where every day has already been migrated, and unsets
 * legacy fields after copying. Runs once at startup; cost is one read + one write
 * per plan with at least one legacy day.
 */
@Component
class PlanDayMultiWorkoutMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanDayMultiWorkoutMigration.class);
    private static final String COLLECTION = "training_plans";

    private final MongoTemplate mongoTemplate;

    PlanDayMultiWorkoutMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        long migrated = 0;
        for (Document plan : mongoTemplate.getCollection(COLLECTION).find()) {
            if (migratePlan(plan)) {
                mongoTemplate.getCollection(COLLECTION).replaceOne(
                        new Document("_id", plan.get("_id")), plan);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("PlanDayMultiWorkoutMigration: migrated {} plan(s) to multi-workout day shape.", migrated);
        }
    }

    private static boolean migratePlan(Document plan) {
        List<Document> weeks = plan.getList("weeks", Document.class);
        if (weeks == null) return false;
        boolean changed = false;
        for (Document week : weeks) {
            List<Document> days = week.getList("days", Document.class);
            if (days == null) continue;
            for (Document day : days) {
                if (migrateField(day, "trainingId", "trainingIds")) changed = true;
                if (migrateField(day, "scheduledWorkoutId", "scheduledWorkoutIds")) changed = true;
            }
        }
        return changed;
    }

    private static boolean migrateField(Document day, String legacyKey, String newKey) {
        boolean changed = false;
        Object legacy = day.get(legacyKey);
        if (legacy != null) {
            List<Object> list = new ArrayList<>();
            // Carry over any pre-existing list values so we never drop data.
            Object existing = day.get(newKey);
            if (existing instanceof List<?> existingList) list.addAll(existingList);
            if (legacy instanceof String s && !s.isBlank() && !list.contains(s)) list.add(s);
            day.put(newKey, list);
            day.remove(legacyKey);
            changed = true;
        } else if (!day.containsKey(newKey)) {
            day.put(newKey, new ArrayList<>());
            changed = true;
        }
        return changed;
    }
}
