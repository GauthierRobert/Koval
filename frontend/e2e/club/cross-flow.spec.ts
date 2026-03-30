import { test, expect } from '../fixtures/base.fixture';
import { CalendarPage } from '../page-objects/calendar.page';
import { ClubDetailPage } from '../page-objects/club-detail.page';
import {
  createClub,
  deleteClub,
  joinClub,
  createClubSession,
  joinClubSession,
  createTraining,
  linkTrainingToSession,
  deleteTraining,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Club - Cross Flow', () => {
  let clubId: string;
  let trainingId: string;

  test.afterEach(async ({ apiContext, coach }) => {
    if (clubId) await deleteClub(apiContext, coach.token, clubId).catch(() => {});
    if (trainingId) await deleteTraining(apiContext, coach.token, trainingId).catch(() => {});
  });

  test('full club session flow: create → link training → athlete joins → sees in calendar', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Step 1: Create club and training
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Cross Flow Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;

    const training = await createTraining(apiContext, coach.token, {
      title: 'Cross Flow Training',
      sportType: 'CYCLING',
    });
    trainingId = training.id;

    // Athlete joins club
    await joinClub(apiContext, athlete.token, clubId);

    // Step 2: Create session for tomorrow
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Cross Flow Session',
      scheduledAt: tomorrow.toISOString(),
      sport: 'CYCLING',
    });

    // Step 3: Link training to session
    await linkTrainingToSession(apiContext, coach.token, clubId, session.id, trainingId);

    // Step 4: Athlete joins session
    await joinClubSession(apiContext, athlete.token, clubId, session.id);

    // Step 5: Verify athlete sees session in club detail
    await injectAuth(page, athlete);
    const clubDetail = new ClubDetailPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('sessions');

    const sessionCard = clubDetail.getSessionCard('Cross Flow Session');
    await expect(sessionCard).toBeVisible({ timeout: 10_000 });

    // Verify linked training is shown
    const linkedTraining = clubDetail.getLinkedTraining('Cross Flow Session');
    await expect(linkedTraining).toBeVisible();
    await expect(linkedTraining).toContainText('Cross Flow Training');

    // Verify attending badge
    await expect(clubDetail.getSessionAttendingBadge('Cross Flow Session')).toBeVisible();

    // Step 6: Verify session appears in calendar
    const calendar = new CalendarPage(page);
    await calendar.goto();

    // Club session should be visible
    const clubSessionCard = calendar.getClubSessionCard('Cross Flow Session');
    await expect(clubSessionCard).toBeVisible({ timeout: 10_000 });
  });

  test('coach assigns workout from club → athlete sees in calendar with club metadata', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Create club
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Assign Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;

    // Create training
    const training = await createTraining(apiContext, coach.token, {
      title: 'Club Assigned Workout',
    });
    trainingId = training.id;

    // Athlete joins club
    await joinClub(apiContext, athlete.token, clubId);

    // Assign training to athlete from club context
    const today = new Date().toISOString().split('T')[0];
    const response = await apiContext.post('http://localhost:8080/api/coach/assign', {
      headers: { Authorization: `Bearer ${coach.token}` },
      data: {
        trainingId,
        athleteIds: [athlete.id],
        scheduledDate: today,
        clubId: clubId,
      },
    });
    expect(response.ok()).toBeTruthy();

    // Athlete navigates to calendar
    await injectAuth(page, athlete);
    const calendar = new CalendarPage(page);
    await calendar.goto();

    // Workout should be visible with club metadata
    const workoutCard = calendar.getWorkoutCard('Club Assigned Workout');
    await expect(workoutCard).toBeVisible({ timeout: 10_000 });
  });
});
