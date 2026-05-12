package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanScheduleSyncServiceTest {

    @Mock
    private ScheduledWorkoutRepository scheduledWorkoutRepository;
    @Mock
    private TrainingRepository trainingRepository;

    private PlanScheduleSyncService service;

    @BeforeEach
    void setUp() {
        service = new PlanScheduleSyncService(scheduledWorkoutRepository, trainingRepository);
        // Most tests just want saveAll to round-trip with deterministic IDs.
        AtomicInteger counter = new AtomicInteger();
        lenient().when(scheduledWorkoutRepository.saveAll(anyIterable()))
                .thenAnswer(inv -> {
                    Iterable<ScheduledWorkout> arg = inv.getArgument(0);
                    List<ScheduledWorkout> saved = new ArrayList<>();
                    for (ScheduledWorkout sw : arg) {
                        if (sw.getId() == null) sw.setId("sw-" + counter.incrementAndGet());
                        saved.add(sw);
                    }
                    return saved;
                });
    }

    private TrainingPlan tripleTrainingDayPlan() {
        // Single Monday holding three workouts (e.g. AM swim, midday bike, PM run)
        PlanDay monday = new PlanDay();
        monday.setDayOfWeek(DayOfWeek.MONDAY);
        monday.setTrainingIds(new ArrayList<>(List.of("swim-1", "bike-1", "run-1")));

        PlanWeek week = new PlanWeek();
        week.setWeekNumber(1);
        week.setDays(new ArrayList<>(List.of(monday)));

        TrainingPlan plan = new TrainingPlan();
        plan.setId("plan-1");
        plan.setStartDate(LocalDate.of(2030, 1, 7)); // a Monday
        plan.setDurationWeeks(1);
        plan.setSportType(SportType.BRICK);
        plan.setWeeks(new ArrayList<>(List.of(week)));
        return plan;
    }

    private CyclingTraining bike(String id, Integer tss, Double iff) {
        CyclingTraining t = new CyclingTraining();
        t.setId(id);
        t.setSportType(SportType.CYCLING);
        t.setEstimatedTss(tss);
        t.setEstimatedIf(iff);
        return t;
    }

    private SwimmingTraining swim(String id, Integer tss) {
        SwimmingTraining t = new SwimmingTraining();
        t.setId(id);
        t.setSportType(SportType.SWIMMING);
        t.setEstimatedTss(tss);
        return t;
    }

    @Test
    @DisplayName("buildAndPersistScheduledWorkouts emits one ScheduledWorkout per (training × athlete)")
    void buildsPerTrainingPerAthlete() {
        TrainingPlan plan = tripleTrainingDayPlan();
        Map<String, Training> byId = Map.of(
                "swim-1", swim("swim-1", 30),
                "bike-1", bike("bike-1", 60, 0.8),
                "run-1", bike("run-1", 45, 0.75));

        service.buildAndPersistScheduledWorkouts(plan, List.of("athlete-A", "athlete-B"),
                "coach-1", LocalDate.of(2030, 1, 1), byId);

        // 3 trainings × 2 athletes = 6 scheduled workouts.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScheduledWorkout>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduledWorkoutRepository).saveAll(captor.capture());
        List<ScheduledWorkout> saved = captor.getValue();
        assertEquals(6, saved.size());

        // Each athlete should see swim + bike + run.
        long aWorkouts = saved.stream().filter(sw -> "athlete-A".equals(sw.getAthleteId())).count();
        long bWorkouts = saved.stream().filter(sw -> "athlete-B".equals(sw.getAthleteId())).count();
        assertEquals(3, aWorkouts);
        assertEquals(3, bWorkouts);

        // Day's scheduledWorkoutIds list should hold every saved id (one per athlete per training).
        PlanDay monday = plan.getWeeks().getFirst().getDays().getFirst();
        assertEquals(6, monday.getScheduledWorkoutIds().size());
    }

    @Test
    @DisplayName("Each ScheduledWorkout copies its training's TSS/IF estimates")
    void copiesPerTrainingMetrics() {
        TrainingPlan plan = tripleTrainingDayPlan();
        Map<String, Training> byId = Map.of(
                "swim-1", swim("swim-1", 30),
                "bike-1", bike("bike-1", 60, 0.8),
                "run-1", bike("run-1", 45, 0.75));

        service.buildAndPersistScheduledWorkouts(plan, List.of("athlete-A"),
                "athlete-A", LocalDate.of(2030, 1, 1), byId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScheduledWorkout>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduledWorkoutRepository).saveAll(captor.capture());
        Map<String, ScheduledWorkout> byTrainingId = captor.getValue().stream()
                .collect(java.util.stream.Collectors.toMap(ScheduledWorkout::getTrainingId, sw -> sw));

        assertEquals(30, byTrainingId.get("swim-1").getTss());
        assertEquals(60, byTrainingId.get("bike-1").getTss());
        assertEquals(0.8, byTrainingId.get("bike-1").getIntensityFactor());
        assertEquals(45, byTrainingId.get("run-1").getTss());
    }

    @Test
    @DisplayName("Days before the cutoff are skipped (past weeks of a re-activated plan)")
    void skipsDaysBeforeCutoff() {
        TrainingPlan plan = tripleTrainingDayPlan();
        Map<String, Training> byId = Map.of(
                "swim-1", swim("swim-1", 30),
                "bike-1", bike("bike-1", 60, 0.8),
                "run-1", bike("run-1", 45, 0.75));

        // Cutoff after the only week — nothing should be persisted.
        service.buildAndPersistScheduledWorkouts(plan, List.of("athlete-A"),
                "athlete-A", LocalDate.of(2030, 12, 31), byId);

        verifyNoInteractions(scheduledWorkoutRepository);
    }

    @Test
    @DisplayName("fetchTrainingsForPlan flattens IDs across days and dedupes")
    void fetchFlattensAndDedupesIds() {
        // Two days with overlapping trainings; expect a single deduped findAllById call.
        PlanDay mon = new PlanDay();
        mon.setDayOfWeek(DayOfWeek.MONDAY);
        mon.setTrainingIds(new ArrayList<>(List.of("bike-1", "swim-1")));
        PlanDay wed = new PlanDay();
        wed.setDayOfWeek(DayOfWeek.WEDNESDAY);
        wed.setTrainingIds(new ArrayList<>(List.of("bike-1", "run-1")));

        PlanWeek week = new PlanWeek();
        week.setWeekNumber(1);
        week.setDays(new ArrayList<>(List.of(mon, wed)));

        TrainingPlan plan = new TrainingPlan();
        plan.setWeeks(new ArrayList<>(List.of(week)));

        when(trainingRepository.findAllById(anyIterable())).thenReturn(List.of(
                swim("swim-1", 30), bike("bike-1", 60, 0.8), bike("run-1", 45, 0.75)));

        Map<String, Training> map = service.fetchTrainingsForPlan(plan);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<String>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(trainingRepository).findAllById(captor.capture());
        List<String> requestedIds = new ArrayList<>();
        captor.getValue().forEach(requestedIds::add);
        assertEquals(3, requestedIds.size(), "bike-1 should be deduped");
        assertTrue(requestedIds.containsAll(List.of("swim-1", "bike-1", "run-1")));
        assertEquals(3, map.size());
    }

    @Test
    @DisplayName("Empty plan does not query the training repository at all")
    void fetchEmptyPlanShortCircuits() {
        TrainingPlan plan = new TrainingPlan();
        Map<String, Training> map = service.fetchTrainingsForPlan(plan);
        assertTrue(map.isEmpty());
        verifyNoInteractions(trainingRepository);
    }

    @Test
    @DisplayName("Persisted ScheduledWorkout carries plan id, athlete id and PENDING status")
    void scheduledWorkoutHasCorrectShape() {
        TrainingPlan plan = tripleTrainingDayPlan();
        Map<String, Training> byId = Map.of(
                "swim-1", swim("swim-1", 30),
                "bike-1", bike("bike-1", 60, 0.8),
                "run-1", bike("run-1", 45, 0.75));

        service.buildAndPersistScheduledWorkouts(plan, List.of("athlete-A"),
                "athlete-A", LocalDate.of(2030, 1, 1), byId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScheduledWorkout>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduledWorkoutRepository).saveAll(captor.capture());
        for (ScheduledWorkout sw : captor.getValue()) {
            assertEquals("plan-1", sw.getPlanId());
            assertEquals("athlete-A", sw.getAthleteId());
            assertEquals(ScheduleStatus.PENDING, sw.getStatus());
            assertEquals(LocalDate.of(2030, 1, 7), sw.getScheduledDate());
        }
    }
}
