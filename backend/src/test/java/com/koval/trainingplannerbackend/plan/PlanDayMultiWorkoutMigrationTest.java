package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.BaseIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-ish tests for {@link PlanDayMultiWorkoutMigration}. The migration operates
 * on raw BSON {@link Document}s — we exercise its conversion rules directly through
 * MongoTemplate instead of mocking the Mongo driver. Uses Testcontainers Mongo via
 * {@link BaseIntegrationTest}.
 */
class PlanDayMultiWorkoutMigrationTest extends BaseIntegrationTest {

    @Autowired
    private PlanDayMultiWorkoutMigration migration;

    @Test
    @DisplayName("Converts legacy trainingId/scheduledWorkoutId fields to list shape")
    void migratesLegacyFields() throws Exception {
        Document legacyDay = new Document()
                .append("dayOfWeek", "MONDAY")
                .append("trainingId", "training-1")
                .append("scheduledWorkoutId", "sw-1");
        Document legacyWeek = new Document()
                .append("weekNumber", 1)
                .append("days", List.of(legacyDay));
        Document legacyPlan = new Document()
                .append("_id", "plan-legacy")
                .append("title", "Legacy plan")
                .append("weeks", List.of(legacyWeek));

        mongoTemplate.getCollection("training_plans").insertOne(legacyPlan);

        migration.run(null);

        Document migrated = mongoTemplate.getCollection("training_plans")
                .find(new Document("_id", "plan-legacy"))
                .first();
        Document day = migrated.getList("weeks", Document.class).getFirst()
                .getList("days", Document.class).getFirst();

        assertFalse(day.containsKey("trainingId"), "legacy trainingId should be removed");
        assertFalse(day.containsKey("scheduledWorkoutId"), "legacy scheduledWorkoutId should be removed");
        assertEquals(List.of("training-1"), day.getList("trainingIds", String.class));
        assertEquals(List.of("sw-1"), day.getList("scheduledWorkoutIds", String.class));
    }

    @Test
    @DisplayName("Initializes empty lists on a day with neither field set")
    void initializesEmptyListsOnBareDay() throws Exception {
        Document bareDay = new Document().append("dayOfWeek", "TUESDAY");
        Document week = new Document().append("weekNumber", 1).append("days", List.of(bareDay));
        Document plan = new Document()
                .append("_id", "plan-bare")
                .append("weeks", List.of(week));

        mongoTemplate.getCollection("training_plans").insertOne(plan);

        migration.run(null);

        Document migrated = mongoTemplate.getCollection("training_plans")
                .find(new Document("_id", "plan-bare"))
                .first();
        Document day = migrated.getList("weeks", Document.class).getFirst()
                .getList("days", Document.class).getFirst();

        assertTrue(day.getList("trainingIds", String.class).isEmpty());
        assertTrue(day.getList("scheduledWorkoutIds", String.class).isEmpty());
    }

    @Test
    @DisplayName("Idempotent: a second run on already-migrated docs is a no-op")
    void idempotent() throws Exception {
        Document day = new Document()
                .append("dayOfWeek", "WEDNESDAY")
                .append("trainingIds", List.of("t1", "t2"))
                .append("scheduledWorkoutIds", List.of("sw1"));
        Document week = new Document().append("weekNumber", 1).append("days", List.of(day));
        Document plan = new Document()
                .append("_id", "plan-already")
                .append("weeks", List.of(week));

        mongoTemplate.getCollection("training_plans").insertOne(plan);

        migration.run(null);
        migration.run(null);

        Document migrated = mongoTemplate.getCollection("training_plans")
                .find(new Document("_id", "plan-already"))
                .first();
        Document migratedDay = migrated.getList("weeks", Document.class).getFirst()
                .getList("days", Document.class).getFirst();
        assertEquals(List.of("t1", "t2"), migratedDay.getList("trainingIds", String.class));
        assertEquals(List.of("sw1"), migratedDay.getList("scheduledWorkoutIds", String.class));
    }

    @Test
    @DisplayName("Merges legacy scalar with a pre-existing list value if both are present")
    void mergesLegacyWithExistingList() throws Exception {
        // A document that somehow has both shapes — the migrator must not drop data.
        Document day = new Document()
                .append("dayOfWeek", "FRIDAY")
                .append("trainingId", "legacy-id")
                .append("trainingIds", List.of("already-there"));
        Document week = new Document().append("weekNumber", 1).append("days", List.of(day));
        Document plan = new Document()
                .append("_id", "plan-mixed")
                .append("weeks", List.of(week));

        mongoTemplate.getCollection("training_plans").insertOne(plan);

        migration.run(null);

        Document migrated = mongoTemplate.getCollection("training_plans")
                .find(new Document("_id", "plan-mixed"))
                .first();
        Document migratedDay = migrated.getList("weeks", Document.class).getFirst()
                .getList("days", Document.class).getFirst();
        assertFalse(migratedDay.containsKey("trainingId"));
        assertEquals(List.of("already-there", "legacy-id"),
                migratedDay.getList("trainingIds", String.class));
    }
}
