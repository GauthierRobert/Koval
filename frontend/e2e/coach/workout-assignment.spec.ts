import { test, expect } from '../fixtures/base.fixture';
import { CoachDashboardPage } from '../page-objects/coach-dashboard.page';
import { TrainingModalPage } from '../page-objects/training-modal.page';
import {
  addAthleteToCoachGroup,
  createTraining,
  assignTraining,
  deleteTraining,
  deleteScheduledWorkout,
  getSchedule,
} from '../fixtures/test-data.fixture';

test.describe('Coach - Workout Assignment', () => {
  let coachPage: CoachDashboardPage;
  let modal: TrainingModalPage;
  let trainingId: string;

  test.beforeEach(async ({ page, coach, athlete, apiContext }) => {
    await addAthleteToCoachGroup(apiContext, coach.token, athlete.id, 'E2E Group');
    const training = await createTraining(apiContext, coach.token, {
      title: 'E2E Test Workout',
      sportType: 'CYCLING',
      trainingType: 'ENDURANCE',
    });
    trainingId = training.id;
    coachPage = new CoachDashboardPage(page);
    modal = new TrainingModalPage(page);
  });

  test.afterEach(async ({ apiContext, coach }) => {
    if (trainingId) {
      await deleteTraining(apiContext, coach.token, trainingId).catch(() => {});
    }
  });

  test('coach assigns workout to athlete via UI', async ({
    page,
    coach,
    athlete,
    authenticatedCoachPage,
  }) => {
    await coachPage.goto();

    // Select athlete
    await coachPage.selectAthlete(athlete.displayName);

    // Open assign modal
    await coachPage.openAssignModal();
    await expect(modal.modal).toBeVisible();

    // Switch to select existing tab
    await modal.switchToSelectTab();

    // Select the training
    await modal.selectTraining('E2E Test Workout');

    // Set date (tomorrow)
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dateStr = tomorrow.toISOString().split('T')[0];
    await modal.setDate(dateStr);

    // Submit
    await modal.submit();

    // Wait for success
    await expect(modal.successMessage).toBeVisible({ timeout: 10_000 });
  });

  test('coach views athlete schedule with assigned workout', async ({
    page,
    coach,
    athlete,
    apiContext,
    authenticatedCoachPage,
  }) => {
    // Assign workout via API
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dateStr = tomorrow.toISOString().split('T')[0];
    await assignTraining(apiContext, coach.token, trainingId, [athlete.id], dateStr);

    await coachPage.goto();
    await coachPage.selectAthlete(athlete.displayName);

    // Navigate to the correct week if needed
    // The schedule should show the assigned workout
    const scheduleRow = coachPage.getScheduleRowByTitle('E2E Test Workout');
    await expect(scheduleRow).toBeVisible({ timeout: 10_000 });
  });

  test('coach marks workout complete', async ({
    page,
    coach,
    athlete,
    apiContext,
    authenticatedCoachPage,
  }) => {
    // Assign workout via API
    const today = new Date().toISOString().split('T')[0];
    const assignments = await assignTraining(apiContext, coach.token, trainingId, [athlete.id], today);
    const workoutId = assignments[0].id;

    // Mark complete via API and verify
    const response = await apiContext.post(`http://localhost:8080/api/schedule/${workoutId}/complete`, {
      headers: { Authorization: `Bearer ${athlete.token}` },
    });
    expect(response.ok()).toBeTruthy();

    const workout = await response.json();
    expect(workout.status).toBe('COMPLETED');
  });

  test('coach marks workout skipped', async ({
    page,
    coach,
    athlete,
    apiContext,
    authenticatedCoachPage,
  }) => {
    // Assign workout via API
    const today = new Date().toISOString().split('T')[0];
    const assignments = await assignTraining(apiContext, coach.token, trainingId, [athlete.id], today);
    const workoutId = assignments[0].id;

    // Mark skipped via API and verify
    const response = await apiContext.post(`http://localhost:8080/api/schedule/${workoutId}/skip`, {
      headers: { Authorization: `Bearer ${athlete.token}` },
    });
    expect(response.ok()).toBeTruthy();

    const workout = await response.json();
    expect(workout.status).toBe('SKIPPED');
  });
});
