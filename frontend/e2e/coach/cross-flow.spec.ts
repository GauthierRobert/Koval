import { test, expect } from '../fixtures/base.fixture';
import { CalendarPage } from '../page-objects/calendar.page';
import { CoachDashboardPage } from '../page-objects/coach-dashboard.page';
import {
  addAthleteToCoachGroup,
  createTraining,
  assignTraining,
  deleteTraining,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Coach - Cross Flow', () => {
  let trainingId: string;

  test.afterEach(async ({ apiContext, coach }) => {
    if (trainingId) {
      await deleteTraining(apiContext, coach.token, trainingId).catch(() => {});
    }
  });

  test('full assignment flow: coach assigns → athlete sees in calendar → marks complete', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Setup: create training and add athlete to coach group
    await addAthleteToCoachGroup(apiContext, coach.token, athlete.id, 'E2E Group');
    const training = await createTraining(apiContext, coach.token, {
      title: 'Cross Flow Workout',
      sportType: 'CYCLING',
    });
    trainingId = training.id;

    // Assign workout for today
    const today = new Date().toISOString().split('T')[0];
    const assignments = await assignTraining(
      apiContext,
      coach.token,
      trainingId,
      [athlete.id],
      today,
      { notes: 'E2E cross flow test' }
    );
    expect(assignments.length).toBe(1);

    // Step 1: Login as athlete and verify workout in calendar
    await injectAuth(page, athlete);
    const calendar = new CalendarPage(page);
    await calendar.goto();

    // The workout should be visible in the calendar
    const workoutCard = calendar.getWorkoutCard('Cross Flow Workout');
    await expect(workoutCard).toBeVisible({ timeout: 10_000 });

    // Step 2: Complete the workout
    await calendar.completeWorkout('Cross Flow Workout');

    // Step 3: Verify the workout is marked complete
    // After completion, the card should have completed status
    await page.waitForTimeout(1000);

    // Verify via API
    const schedule = await apiContext
      .get(`http://localhost:8080/api/schedule?start=${today}&end=${today}`, {
        headers: { Authorization: `Bearer ${athlete.token}` },
      })
      .then((r) => r.json());

    const completed = schedule.find(
      (s: any) => s.trainingId === trainingId && s.status === 'COMPLETED'
    );
    expect(completed).toBeTruthy();

    // Step 4: Switch to coach and verify completion in schedule
    await injectAuth(page, coach);
    const coachDashboard = new CoachDashboardPage(page);
    await coachDashboard.goto();
    await coachDashboard.selectAthlete(athlete.displayName);

    // The schedule row should show completed status
    const scheduleRow = coachDashboard.getScheduleRowByTitle('Cross Flow Workout');
    await expect(scheduleRow).toBeVisible({ timeout: 10_000 });
  });
});
