import { test, expect } from '../fixtures/base.fixture';
import { PmcPage } from '../page-objects/pmc.page';
import {
  createSession,
  deleteSession,
  createTraining,
  assignTraining,
  deleteTraining,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('PMC (Performance Management Chart)', () => {
  let pmcPage: PmcPage;
  const createdSessionIds: string[] = [];
  let trainingId: string | null = null;

  test.afterEach(async ({ apiContext, athlete, coach }) => {
    for (const id of createdSessionIds) {
      await deleteSession(apiContext, athlete.token, id).catch(() => {});
    }
    createdSessionIds.length = 0;
    if (trainingId) {
      await deleteTraining(apiContext, coach.token, trainingId).catch(() => {});
      trainingId = null;
    }
  });

  test('PMC page loads with CTL, ATL, TSB badges', async ({ page, athlete, apiContext }) => {
    // Create a session so PMC has data
    const session = await createSession(apiContext, athlete.token, {
      title: 'PMC Data Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 220,
      tss: 80,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    pmcPage = new PmcPage(page);
    await pmcPage.goto();

    // CTL, ATL, TSB badges should be visible
    await expect(pmcPage.ctlBadge).toBeVisible({ timeout: 10_000 });
    await expect(pmcPage.atlBadge).toBeVisible();
    await expect(pmcPage.tsbBadge).toBeVisible();

    // Badge values should contain numbers
    await expect(pmcPage.getCtlValue()).toBeVisible();
    await expect(pmcPage.getAtlValue()).toBeVisible();
    await expect(pmcPage.getTsbValue()).toBeVisible();
  });

  test('PMC chart renders', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'PMC Chart Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 200,
      tss: 70,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    pmcPage = new PmcPage(page);
    await pmcPage.goto();

    // Chart component should be visible
    await expect(pmcPage.pmcChart).toBeVisible({ timeout: 10_000 });
  });

  test('refresh PMC updates badges', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'PMC Refresh Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 230,
      tss: 90,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    pmcPage = new PmcPage(page);
    await pmcPage.goto();

    await expect(pmcPage.ctlBadge).toBeVisible({ timeout: 10_000 });

    // Click refresh
    await pmcPage.refresh();
    await page.waitForTimeout(1000);

    // Badges should still be visible after refresh
    await expect(pmcPage.ctlBadge).toBeVisible();
    await expect(pmcPage.atlBadge).toBeVisible();
    await expect(pmcPage.tsbBadge).toBeVisible();
  });

  test('scheduled workouts count shows in pill', async ({
    page,
    athlete,
    coach,
    apiContext,
  }) => {
    // Create and assign a training to athlete
    const training = await createTraining(apiContext, coach.token, {
      title: 'PMC Scheduled Workout',
    });
    trainingId = training.id;

    // Need coach to have athlete relationship first
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = tomorrow.toISOString().split('T')[0];

    await assignTraining(apiContext, coach.token, trainingId, [athlete.id], tomorrowStr);

    // Login as athlete and check PMC
    await injectAuth(page, athlete);
    pmcPage = new PmcPage(page);
    await pmcPage.goto();

    // Schedule pill should be visible
    await expect(pmcPage.schedulePill).toBeVisible({ timeout: 10_000 });
  });
});
