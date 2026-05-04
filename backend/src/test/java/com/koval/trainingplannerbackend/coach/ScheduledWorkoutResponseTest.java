package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledWorkoutResponseTest {

    private ScheduledWorkout newScheduled(String id, String trainingId, String athleteId,
            ScheduleStatus status, Integer tss, Double intensityFactor) {
        ScheduledWorkout sw = new ScheduledWorkout();
        sw.setId(id);
        sw.setTrainingId(trainingId);
        sw.setAthleteId(athleteId);
        sw.setAssignedBy("coach1");
        sw.setScheduledDate(LocalDate.of(2026, 5, 1));
        sw.setStatus(status);
        sw.setTss(tss);
        sw.setIntensityFactor(intensityFactor);
        sw.setNotes("notes");
        sw.setCompletedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        return sw;
    }

    @Nested
    class FromAssignedWorkout {

        @Test
        void copiesAllScalarFields() {
            ScheduledWorkout sw = newScheduled("sw1", "t1", "a1", ScheduleStatus.PENDING, 80, 0.85);
            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.from(sw,
                    "FTP Booster", TrainingType.THRESHOLD, 3600, SportType.CYCLING,
                    null, null,
                    null, null, null, null);

            assertEquals("sw1", r.id());
            assertEquals("t1", r.trainingId());
            assertEquals("a1", r.athleteId());
            assertEquals("coach1", r.assignedBy());
            assertEquals(LocalDate.of(2026, 5, 1), r.scheduledDate());
            assertEquals(ScheduleStatus.PENDING, r.status());
            assertEquals("notes", r.notes());
            assertEquals(80, r.tss());
            assertEquals(0.85, r.intensityFactor());
            assertEquals("FTP Booster", r.trainingTitle());
            assertEquals(TrainingType.THRESHOLD, r.trainingType());
            assertEquals(3600, r.totalDurationSeconds());
            assertEquals(SportType.CYCLING, r.sportType());
            assertFalse(r.isClubSession());
            assertNull(r.clubName());
            assertNull(r.planId());
        }

        @Test
        void preservesActualTssOverEstimate() {
            ScheduledWorkout sw = newScheduled("sw1", "t1", "a1", ScheduleStatus.PENDING, 90, 0.9);
            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.from(sw,
                    "T", TrainingType.THRESHOLD, 3600, SportType.CYCLING,
                    50, 0.5,  // estimates ignored when actual present
                    null, null, null, null);

            assertEquals(90, r.tss(), "actual TSS should win over estimate");
            assertEquals(0.9, r.intensityFactor());
        }

        @Test
        void fallsBackToEstimateWhenActualIsNull() {
            ScheduledWorkout sw = newScheduled("sw1", "t1", "a1", ScheduleStatus.PENDING, null, null);
            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.from(sw,
                    "T", TrainingType.THRESHOLD, 3600, SportType.CYCLING,
                    50, 0.5,
                    null, null, null, null);

            assertEquals(50, r.tss());
            assertEquals(0.5, r.intensityFactor());
        }

        @Test
        void capturesPlanContext() {
            ScheduledWorkout sw = newScheduled("sw1", "t1", "a1", ScheduleStatus.PENDING, 70, 0.8);
            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.from(sw,
                    "T", TrainingType.ENDURANCE, 3600, SportType.CYCLING,
                    null, null,
                    "plan1", "12-Week Build", 3, "Build 1");

            assertEquals("plan1", r.planId());
            assertEquals("12-Week Build", r.planTitle());
            assertEquals(3, r.weekNumber());
            assertEquals("Build 1", r.weekLabel());
        }
    }

    @Nested
    class FromClubSession {

        private ClubTrainingSession clubSession() {
            ClubTrainingSession s = new ClubTrainingSession();
            s.setId("sess1");
            s.setTitle("Saturday Group Ride");
            s.setSport("CYCLING");
            s.setScheduledAt(LocalDateTime.of(2026, 5, 1, 9, 0));
            s.setDescription("Long ride at endurance pace");
            s.setResponsibleCoachId("coach2");
            s.setDurationMinutes(120);
            s.setCreatedAt(LocalDateTime.of(2026, 4, 1, 8, 0));
            return s;
        }

        @Test
        void noLinkedTraining_usesSessionFields() {
            ClubTrainingSession s = clubSession();

            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.fromClubSession(s, "Riders Club", "Pro group", null);

            assertEquals("sess1", r.id());
            assertEquals("Saturday Group Ride", r.trainingTitle());
            assertEquals(LocalDate.of(2026, 5, 1), r.scheduledDate());
            assertEquals(ScheduleStatus.PENDING, r.status());
            assertEquals("Long ride at endurance pace", r.notes());
            assertEquals(120 * 60, r.totalDurationSeconds());
            assertEquals(SportType.CYCLING, r.sportType());
            assertNull(r.tss());
            assertNull(r.intensityFactor());
            assertTrue(r.isClubSession());
            assertEquals("Riders Club", r.clubName());
            assertEquals("Pro group", r.clubGroupName());
            assertEquals("coach2", r.assignedBy());
        }

        @Test
        void unknownSport_yieldsNullSportType() {
            ClubTrainingSession s = clubSession();
            s.setSport("WALKING");

            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.fromClubSession(s, "C", null, null);

            assertNull(r.sportType());
        }

        @Test
        void nullDurationMinutes_yieldsNullDurationSeconds() {
            ClubTrainingSession s = clubSession();
            s.setDurationMinutes(null);

            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.fromClubSession(s, "C", null, null);

            assertNull(r.totalDurationSeconds());
        }

        @Test
        void linkedTraining_overridesSessionFields() {
            ClubTrainingSession s = clubSession();
            CyclingTraining t = new CyclingTraining();
            t.setId("t1");
            t.setTitle("Linked FTP Workout");
            t.setEstimatedTss(120);
            t.setEstimatedIf(0.95);
            t.setEstimatedDurationSeconds(5400);
            t.setSportType(SportType.CYCLING);
            t.setTrainingType(TrainingType.THRESHOLD);

            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.fromClubSession(s, "C", "G", t);

            assertEquals("Linked FTP Workout", r.trainingTitle());
            assertEquals(120, r.tss());
            assertEquals(0.95, r.intensityFactor());
            assertEquals(5400, r.totalDurationSeconds());
            assertEquals(TrainingType.THRESHOLD, r.trainingType());
            assertEquals(SportType.CYCLING, r.sportType());
        }

        @Test
        void linkedSwimTraining_setsSwimmingSport() {
            ClubTrainingSession s = clubSession();
            SwimmingTraining t = new SwimmingTraining();
            t.setId("sw");
            t.setTitle("Threshold");
            t.setSportType(SportType.SWIMMING);
            t.setTrainingType(TrainingType.THRESHOLD);

            ScheduledWorkoutResponse r = ScheduledWorkoutResponse.fromClubSession(s, "C", null, t);

            assertEquals(SportType.SWIMMING, r.sportType());
            assertEquals(TrainingType.THRESHOLD, r.trainingType());
        }
    }
}
