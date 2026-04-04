package com.koval.trainingplannerbackend.ai.anonymization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnonymizationContextTest {

    private AnonymizationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new AnonymizationContext();
    }

    @Nested
    class AthleteAnonymization {

        @Test
        void assignsSequentialAliases() {
            String alias1 = ctx.anonymizeAthlete("mongo-id-abc");
            String alias2 = ctx.anonymizeAthlete("mongo-id-def");

            assertEquals("Athlete-1", alias1);
            assertEquals("Athlete-2", alias2);
        }

        @Test
        void returnsSameAliasForSameId() {
            String first = ctx.anonymizeAthlete("mongo-id-abc");
            String second = ctx.anonymizeAthlete("mongo-id-abc");

            assertEquals(first, second);
            assertEquals("Athlete-1", first);
        }

        @Test
        void deAnonymizesCorrectly() {
            ctx.anonymizeAthlete("mongo-id-abc");

            assertEquals("mongo-id-abc", ctx.deAnonymize("Athlete-1"));
        }

        @Test
        void deAnonymizeReturnsInputForUnknownAlias() {
            assertEquals("unknown-alias", ctx.deAnonymize("unknown-alias"));
        }
    }

    @Nested
    class GroupAnonymization {

        @Test
        void assignsGroupAliases() {
            String alias = ctx.anonymizeGroup("group-real-id");

            assertEquals("Group-1", alias);
        }

        @Test
        void deAnonymizesGroupAlias() {
            ctx.anonymizeGroup("group-real-id");

            assertEquals("group-real-id", ctx.deAnonymize("Group-1"));
        }
    }

    @Nested
    class ClubAnonymization {

        @Test
        void assignsClubAliases() {
            String alias = ctx.anonymizeClub("club-real-id");

            assertEquals("Club-1", alias);
        }

        @Test
        void deAnonymizesClubAlias() {
            ctx.anonymizeClub("club-real-id");

            assertEquals("club-real-id", ctx.deAnonymize("Club-1"));
        }
    }

    @Nested
    class BatchDeAnonymization {

        @Test
        void deAnonymizesListOfAliases() {
            ctx.anonymizeAthlete("id-1");
            ctx.anonymizeAthlete("id-2");
            ctx.anonymizeAthlete("id-3");

            List<String> result = ctx.deAnonymizeAll(List.of("Athlete-1", "Athlete-3"));

            assertEquals(List.of("id-1", "id-3"), result);
        }

        @Test
        void passesUnknownAliasesThrough() {
            ctx.anonymizeAthlete("id-1");

            List<String> result = ctx.deAnonymizeAll(List.of("Athlete-1", "raw-id-unknown"));

            assertEquals(List.of("id-1", "raw-id-unknown"), result);
        }
    }

    @Nested
    class CrossTypeIsolation {

        @Test
        void sameRealIdUsedByDifferentTypesReturnsSameAlias() {
            // If the same real ID is anonymized first as an athlete, the mapping is reused
            String athleteAlias = ctx.anonymizeAthlete("shared-id");
            String groupAlias = ctx.anonymizeGroup("shared-id");

            // Both should return the same alias since it's the same real ID
            assertEquals(athleteAlias, groupAlias);
        }

        @Test
        void differentRealIdsGetDifferentAliases() {
            String athlete = ctx.anonymizeAthlete("athlete-id");
            String group = ctx.anonymizeGroup("group-id");
            String club = ctx.anonymizeClub("club-id");

            assertNotEquals(athlete, group);
            assertNotEquals(group, club);
            assertNotEquals(athlete, club);
        }
    }

    @Nested
    class GetAlias {

        @Test
        void returnsNullForUnmappedId() {
            assertNull(ctx.getAlias("not-yet-mapped"));
        }

        @Test
        void returnsAliasForMappedId() {
            ctx.anonymizeAthlete("some-id");

            assertEquals("Athlete-1", ctx.getAlias("some-id"));
        }
    }
}
