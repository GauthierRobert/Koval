import { test, expect } from '../fixtures/base.fixture';
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

test.describe('Club - Sessions', () => {
  let clubDetailPage: ClubDetailPage;
  let clubId: string;
  let trainingId: string;

  test.beforeEach(async ({ page, coach, apiContext }) => {
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Sessions Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;
    clubDetailPage = new ClubDetailPage(page);
  });

  test.afterEach(async ({ apiContext, coach }) => {
    await deleteClub(apiContext, coach.token, clubId).catch(() => {});
    if (trainingId) {
      await deleteTraining(apiContext, coach.token, trainingId).catch(() => {});
    }
  });

  test('create one-off session via UI', async ({ page, coach, authenticatedCoachPage }) => {
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    // Open form
    await clubDetailPage.getAddSessionBtn().click();
    await expect(clubDetailPage.getSessionFormModal()).toBeVisible();

    // Fill form
    await clubDetailPage.getSessionFormTitle().fill('E2E Session');
    await clubDetailPage.getSessionFormDayOfWeek().selectOption('TUESDAY');
    await clubDetailPage.getSessionFormTimeOfDay().fill('09:00');

    // Submit
    await clubDetailPage.getSessionFormSubmitBtn().click();
    await page.waitForTimeout(1000);

    // Session card should appear
    const sessionCard = clubDetailPage.getSessionCard('E2E Session');
    await expect(sessionCard).toBeVisible({ timeout: 10_000 });
  });

  test('join and leave a club session', async ({ page, coach, athlete, apiContext }) => {
    // Create session via API
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Join Test Session',
      scheduledAt: tomorrow.toISOString(),
    });

    // Join club as athlete
    await joinClub(apiContext, athlete.token, clubId);

    // Login as athlete
    await injectAuth(page, athlete);
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    // Join session
    const sessionCard = clubDetailPage.getSessionCard('Join Test Session');
    await expect(sessionCard).toBeVisible({ timeout: 10_000 });
    await clubDetailPage.joinSession('Join Test Session');

    // Verify attending badge
    await expect(clubDetailPage.getSessionAttendingBadge('Join Test Session')).toBeVisible();

    // Leave session
    await clubDetailPage.leaveSession('Join Test Session');

    // Attending badge should disappear
    await expect(clubDetailPage.getSessionAttendingBadge('Join Test Session')).not.toBeVisible({
      timeout: 5000,
    });
  });

  test('waiting list when session is full', async ({
    page,
    coach,
    athlete,
    athlete2,
    apiContext,
  }) => {
    // Create session with max 1 participant
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Full Session',
      scheduledAt: tomorrow.toISOString(),
      maxParticipants: 1,
    });

    // Both athletes join the club
    await joinClub(apiContext, athlete.token, clubId);
    await joinClub(apiContext, athlete2.token, clubId);

    // First athlete joins (gets in)
    await joinClubSession(apiContext, athlete.token, clubId, session.id);

    // Second athlete joins (goes to waiting list)
    await joinClubSession(apiContext, athlete2.token, clubId, session.id);

    // Login as athlete2 and verify waiting list
    await injectAuth(page, athlete2);
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    const waitingBadge = clubDetailPage.getSessionWaitingBadge('Full Session');
    await expect(waitingBadge).toBeVisible({ timeout: 10_000 });
  });

  test('link training to session', async ({ page, coach, apiContext, authenticatedCoachPage }) => {
    // Create training
    const training = await createTraining(apiContext, coach.token, {
      title: 'E2E Linked Workout',
    });
    trainingId = training.id;

    // Create session
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Linked Session',
      scheduledAt: tomorrow.toISOString(),
    });

    // Link training to session via API
    await linkTrainingToSession(apiContext, coach.token, clubId, session.id, trainingId);

    // Navigate to club and verify linked training is visible
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    const linked = clubDetailPage.getLinkedTraining('Linked Session');
    await expect(linked).toBeVisible({ timeout: 10_000 });
    await expect(linked).toContainText('E2E Linked Workout');
  });
});
