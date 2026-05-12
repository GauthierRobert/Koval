package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingPlanPopulatedTest {

    private TrainingPlan twoWorkoutPlan() {
        PlanDay day = new PlanDay();
        day.setDayOfWeek(DayOfWeek.MONDAY);
        day.setTrainingIds(new java.util.ArrayList<>(List.of("swim-1", "bike-1")));
        day.setScheduledWorkoutIds(new java.util.ArrayList<>(List.of("sw-A", "sw-B")));

        PlanWeek week = new PlanWeek();
        week.setWeekNumber(1);
        week.setDays(new java.util.ArrayList<>(List.of(day)));

        TrainingPlan plan = new TrainingPlan();
        plan.setId("plan-1");
        plan.setTitle("Tri week");
        plan.setSportType(SportType.BRICK);
        plan.setWeeks(new java.util.ArrayList<>(List.of(week)));
        return plan;
    }

    private SwimmingTraining swim() {
        SwimmingTraining t = new SwimmingTraining();
        t.setId("swim-1");
        t.setTitle("Swim CSS");
        t.setSportType(SportType.SWIMMING);
        return t;
    }

    private CyclingTraining bike() {
        CyclingTraining t = new CyclingTraining();
        t.setId("bike-1");
        t.setTitle("FTP Bike");
        t.setSportType(SportType.CYCLING);
        return t;
    }

    @Test
    @DisplayName("DayPopulated resolves every trainingId to a Training in order")
    void populatesAllTrainingsForADay() {
        TrainingPlan plan = twoWorkoutPlan();
        Map<String, Training> resolver = Map.of(
                "swim-1", swim(),
                "bike-1", bike());

        TrainingPlanPopulated populated = TrainingPlanPopulated.from(plan, resolver);

        TrainingPlanPopulated.DayPopulated day = populated.weeks().getFirst().days().getFirst();
        assertEquals(List.of("swim-1", "bike-1"), day.trainingIds());
        assertEquals(List.of("sw-A", "sw-B"), day.scheduledWorkoutIds());
        assertEquals(2, day.trainings().size());
        assertEquals("swim-1", day.trainings().get(0).getId());
        assertEquals("bike-1", day.trainings().get(1).getId());
    }

    @Test
    @DisplayName("Unresolvable training IDs are silently filtered from the trainings list")
    void filtersUnresolvedTrainings() {
        TrainingPlan plan = twoWorkoutPlan();
        Map<String, Training> resolver = Map.of("swim-1", swim()); // bike-1 unresolved

        TrainingPlanPopulated populated = TrainingPlanPopulated.from(plan, resolver);

        TrainingPlanPopulated.DayPopulated day = populated.weeks().getFirst().days().getFirst();
        assertEquals(List.of("swim-1", "bike-1"), day.trainingIds(),
                "IDs are still preserved even when not resolvable");
        assertEquals(1, day.trainings().size());
        assertEquals("swim-1", day.trainings().getFirst().getId());
    }

    @Test
    @DisplayName("collectTrainingIds gathers and dedupes across weeks and days")
    void collectsAndDedupesTrainingIds() {
        PlanDay monday = new PlanDay();
        monday.setDayOfWeek(DayOfWeek.MONDAY);
        monday.setTrainingIds(new java.util.ArrayList<>(List.of("a", "b")));
        PlanDay wednesday = new PlanDay();
        wednesday.setDayOfWeek(DayOfWeek.WEDNESDAY);
        wednesday.setTrainingIds(new java.util.ArrayList<>(List.of("a", "c")));

        PlanWeek week = new PlanWeek();
        week.setWeekNumber(1);
        week.setDays(new java.util.ArrayList<>(List.of(monday, wednesday)));

        TrainingPlan plan = new TrainingPlan();
        plan.setWeeks(new java.util.ArrayList<>(List.of(week)));

        List<String> ids = TrainingPlanPopulated.collectTrainingIds(plan);
        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of("a", "b", "c")));
    }

    @Test
    @DisplayName("Empty trainingIds yields an empty resolved list")
    void emptyDayProducesEmptyTrainings() {
        PlanDay day = new PlanDay();
        day.setDayOfWeek(DayOfWeek.SUNDAY);

        PlanWeek week = new PlanWeek();
        week.setWeekNumber(1);
        week.setDays(new java.util.ArrayList<>(List.of(day)));

        TrainingPlan plan = new TrainingPlan();
        plan.setWeeks(new java.util.ArrayList<>(List.of(week)));

        TrainingPlanPopulated populated = TrainingPlanPopulated.from(plan, Map.of());
        TrainingPlanPopulated.DayPopulated populatedDay = populated.weeks().getFirst().days().getFirst();
        assertTrue(populatedDay.trainingIds().isEmpty());
        assertTrue(populatedDay.scheduledWorkoutIds().isEmpty());
        assertTrue(populatedDay.trainings().isEmpty());
    }

    @Test
    @DisplayName("Mixed-sport day surfaces both trainings preserving order")
    void mixedSportDay() {
        RunningTraining run = new RunningTraining();
        run.setId("run-1");
        run.setSportType(SportType.RUNNING);
        run.setTitle("Tempo run");

        PlanDay day = new PlanDay();
        day.setDayOfWeek(DayOfWeek.SATURDAY);
        day.setTrainingIds(new java.util.ArrayList<>(List.of("bike-1", "run-1")));

        PlanWeek week = new PlanWeek();
        week.setWeekNumber(1);
        week.setDays(new java.util.ArrayList<>(List.of(day)));

        TrainingPlan plan = new TrainingPlan();
        plan.setWeeks(new java.util.ArrayList<>(List.of(week)));

        TrainingPlanPopulated populated = TrainingPlanPopulated.from(plan,
                Map.of("bike-1", bike(), "run-1", run));

        List<Training> trainings = populated.weeks().getFirst().days().getFirst().trainings();
        assertEquals(SportType.CYCLING, trainings.get(0).getSportType());
        assertEquals(SportType.RUNNING, trainings.get(1).getSportType());
    }
}
