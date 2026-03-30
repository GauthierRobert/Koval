import { test, expect } from '../fixtures/base.fixture';
import { CalendarPage } from '../page-objects/calendar.page';
import {
  createClub,
  deleteClub,
  joinClub,
  createClubSession,
  joinClubSession,
  createTraining,
  assignTraining,
  deleteTraining,
  addAthleteToCoachGroup,
  linkTrainingToSession,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Calendar Integration', () => {
  let trainingId: string;
  let clubId: string;
  let calendarPage: CalendarPage;

  test.afterEach(async ({ apiContext, coach }) => {
    if (trainingId) await deleteTraining(apiContext, coach.token, trainingId).catch(() => {});
    if (clubId) await deleteClub(apiContext, coach.token, clubId).catch(() => {});
  });

  test('athlete sees assigned workout in calendar', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Setup
    await addAthleteToCoachGroup(apiContext, coach.token, athlete.id, 'Calendar Group');
    const training = await createTraining(apiContext, coach.token, {
      title: 'Calendar Test Workout',
      sportType: 'CYCLING',
      trainingType: 'THRESHOLD',
    });
    trainingId = training.id;

    // Assign for today
    const today = new Date().toISOString().split('T')[0];
    await assignTraining(apiContext, coach.token, trainingId, [athlete.id], today);

    // Login as athlete
    await injectAuth(page, athlete);
    calendarPage = new CalendarPage(page);
    await calendarPage.goto();

    // Workout should be visible
    const workoutCard = calendarPage.getWorkoutCard('Calendar Test Workout');
    await expect(workoutCard).toBeVisible({ timeout: 10_000 });
  });

  test('athlete sees club sessions in calendar', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Create club
    const club = await createClub(apiContext, coach.token, {
      name: 'Calendar Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;

    // Athlete joins
    await joinClub(apiContext, athlete.token, clubId);

    // Create session for tomorrow
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Calendar Club Session',
      scheduledAt: tomorrow.toISOString(),
      sport: 'CYCLING',
    });

    // Athlete joins session
    await joinClubSession(apiContext, athlete.token, clubId, session.id);

    // Navigate to calendar
    await injectAuth(page, athlete);
    calendarPage = new CalendarPage(page);
    await calendarPage.goto();

    // Navigate to tomorrow if needed
    const clubSessionCard = calendarPage.getClubSessionCard('Calendar Club Session');
    if (!(await clubSessionCard.isVisible().catch(() => false))) {
      await calendarPage.navNext.click();
      await page.waitForTimeout(500);
    }

    await expect(clubSessionCard).toBeVisible({ timeout: 10_000 });
  });

  test('athlete sees linked training from club session in calendar', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Create club
    const club = await createClub(apiContext, coach.token, {
      name: 'Linked Training Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;

    // Create training
    const training = await createTraining(apiContext, coach.token, {
      title: 'Calendar Linked Workout',
    });
    trainingId = training.id;

    // Athlete joins club
    await joinClub(apiContext, athlete.token, clubId);

    // Create session
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Linked Session',
      scheduledAt: tomorrow.toISOString(),
    });

    // Link training
    await linkTrainingToSession(apiContext, coach.token, clubId, session.id, trainingId);

    // Athlete joins
    await joinClubSession(apiContext, athlete.token, clubId, session.id);

    // Navigate to calendar
    await injectAuth(page, athlete);
    calendarPage = new CalendarPage(page);
    await calendarPage.goto();

    // Find the club session
    const clubSessionCard = calendarPage.getClubSessionCard('Linked Session');
    if (!(await clubSessionCard.isVisible().catch(() => false))) {
      await calendarPage.navNext.click();
      await page.waitForTimeout(500);
    }
    await expect(clubSessionCard).toBeVisible({ timeout: 10_000 });

    // The linked training name should be visible in the card
    await expect(clubSessionCard).toContainText('Calendar Linked Workout');
  });

  test('calendar week/month view toggle', async ({
    page,
    coach,
    authenticatedCoachPage,
  }) => {
    calendarPage = new CalendarPage(page);
    await calendarPage.goto();

    // Default should show week view
    await expect(calendarPage.getDayColumns().first()).toBeVisible();

    // Switch to month view
    await calendarPage.monthViewBtn.click();
    await page.waitForTimeout(300);

    // Month grid should be visible
    await expect(calendarPage.getMonthCells().first()).toBeVisible();

    // Switch back to week view
    await calendarPage.weekViewBtn.click();
    await page.waitForTimeout(300);

    await expect(calendarPage.getDayColumns().first()).toBeVisible();
  });

  test('calendar navigation (prev/next/today)', async ({
    page,
    coach,
    authenticatedCoachPage,
  }) => {
    calendarPage = new CalendarPage(page);
    await calendarPage.goto();

    // Navigate next
    await calendarPage.navNext.click();
    await page.waitForTimeout(300);

    // Navigate prev twice (back to last week)
    await calendarPage.navPrev.click();
    await page.waitForTimeout(300);
    await calendarPage.navPrev.click();
    await page.waitForTimeout(300);

    // Navigate to today
    await calendarPage.navToday.click();
    await page.waitForTimeout(300);

    // Today column should have .today class
    const todayColumn = page.locator('.day-column.today');
    await expect(todayColumn).toBeVisible();
  });

  test('complete workout from calendar', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Setup
    await addAthleteToCoachGroup(apiContext, coach.token, athlete.id, 'Complete Group');
    const training = await createTraining(apiContext, coach.token, {
      title: 'Complete From Calendar',
    });
    trainingId = training.id;

    const today = new Date().toISOString().split('T')[0];
    await assignTraining(apiContext, coach.token, trainingId, [athlete.id], today);

    // Login as athlete
    await injectAuth(page, athlete);
    calendarPage = new CalendarPage(page);
    await calendarPage.goto();

    // Find and complete the workout
    const workoutCard = calendarPage.getWorkoutCard('Complete From Calendar');
    await expect(workoutCard).toBeVisible({ timeout: 10_000 });

    await calendarPage.completeWorkout('Complete From Calendar');
    await page.waitForTimeout(1000);

    // Verify completion via API
    const schedule = await apiContext
      .get(`http://localhost:8080/api/schedule?start=${today}&end=${today}`, {
        headers: { Authorization: `Bearer ${athlete.token}` },
      })
      .then((r) => r.json());

    const completed = schedule.find(
      (s: any) => s.trainingId === trainingId && s.status === 'COMPLETED'
    );
    expect(completed).toBeTruthy();
  });

  test('skip workout from calendar', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    await addAthleteToCoachGroup(apiContext, coach.token, athlete.id, 'Skip Group');
    const training = await createTraining(apiContext, coach.token, {
      title: 'Skip From Calendar',
    });
    trainingId = training.id;

    const today = new Date().toISOString().split('T')[0];
    await assignTraining(apiContext, coach.token, trainingId, [athlete.id], today);

    await injectAuth(page, athlete);
    calendarPage = new CalendarPage(page);
    await calendarPage.goto();

    const workoutCard = calendarPage.getWorkoutCard('Skip From Calendar');
    await expect(workoutCard).toBeVisible({ timeout: 10_000 });

    await calendarPage.skipWorkout('Skip From Calendar');
    await page.waitForTimeout(1000);

    // Verify via API
    const schedule = await apiContext
      .get(`http://localhost:8080/api/schedule?start=${today}&end=${today}`, {
        headers: { Authorization: `Bearer ${athlete.token}` },
      })
      .then((r) => r.json());

    const skipped = schedule.find(
      (s: any) => s.trainingId === trainingId && s.status === 'SKIPPED'
    );
    expect(skipped).toBeTruthy();
  });
});
