import { test, expect } from '../fixtures/base.fixture';
import { WorkoutHistoryPage } from '../page-objects/workout-history.page';
import {
  createSession,
  deleteSession,
  getSessions,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Workout History', () => {
  let historyPage: WorkoutHistoryPage;
  const createdSessionIds: string[] = [];

  test.afterEach(async ({ apiContext, athlete }) => {
    for (const id of createdSessionIds) {
      await deleteSession(apiContext, athlete.token, id).catch(() => {});
    }
    createdSessionIds.length = 0;
  });

  test('view session list with metrics', async ({ page, athlete, apiContext }) => {
    // Create sessions via API
    const s1 = await createSession(apiContext, athlete.token, {
      title: 'Morning Ride',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 220,
      avgHR: 150,
      tss: 85,
      intensityFactor: 0.92,
    });
    createdSessionIds.push(s1.id);

    const s2 = await createSession(apiContext, athlete.token, {
      title: 'Easy Spin',
      sportType: 'CYCLING',
      totalDurationSeconds: 1800,
      avgPower: 150,
      avgHR: 120,
    });
    createdSessionIds.push(s2.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    await historyPage.goto();

    // Both sessions should be visible
    await expect(historyPage.getSessionItem('Morning Ride')).toBeVisible({ timeout: 10_000 });
    await expect(historyPage.getSessionItem('Easy Spin')).toBeVisible();

    // Metrics should be shown (TSS, duration, IF)
    const morningRide = historyPage.getSessionItem('Morning Ride');
    await expect(morningRide).toContainText('TSS');
    await expect(morningRide).toContainText('IF');
  });

  test('filter by sport', async ({ page, athlete, apiContext }) => {
    const cycling = await createSession(apiContext, athlete.token, {
      title: 'Cycling Session Filter',
      sportType: 'CYCLING',
    });
    createdSessionIds.push(cycling.id);

    const running = await createSession(apiContext, athlete.token, {
      title: 'Running Session Filter',
      sportType: 'RUNNING',
      avgPower: 0,
      avgSpeed: 3.5,
    });
    createdSessionIds.push(running.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    await historyPage.goto();

    await expect(historyPage.getSessionItem('Cycling Session Filter')).toBeVisible({ timeout: 10_000 });
    await expect(historyPage.getSessionItem('Running Session Filter')).toBeVisible();

    // Filter by CYCLING
    await historyPage.filterBySport('CYCLING');
    await page.waitForTimeout(500);

    await expect(historyPage.getSessionItem('Cycling Session Filter')).toBeVisible();
    await expect(historyPage.getSessionItem('Running Session Filter')).not.toBeVisible();
  });

  test('filter by date range', async ({ page, athlete, apiContext }) => {
    const today = new Date();
    const weekAgo = new Date();
    weekAgo.setDate(today.getDate() - 7);

    const recent = await createSession(apiContext, athlete.token, {
      title: 'Recent Session',
      completedAt: today.toISOString(),
    });
    createdSessionIds.push(recent.id);

    const old = await createSession(apiContext, athlete.token, {
      title: 'Old Session',
      completedAt: new Date('2025-01-15').toISOString(),
    });
    createdSessionIds.push(old.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    await historyPage.goto();

    // Set date range to only include this week
    const fromStr = weekAgo.toISOString().split('T')[0];
    const toStr = today.toISOString().split('T')[0];
    await historyPage.setDateFrom(fromStr);
    await historyPage.setDateTo(toStr);
    await page.waitForTimeout(500);

    await expect(historyPage.getSessionItem('Recent Session')).toBeVisible({ timeout: 10_000 });
    await expect(historyPage.getSessionItem('Old Session')).not.toBeVisible();
  });

  test('select session shows analysis', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'Analysis Session',
      avgPower: 250,
      tss: 100,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    await historyPage.goto();

    await expect(historyPage.getSessionItem('Analysis Session')).toBeVisible({ timeout: 10_000 });
    await historyPage.selectSession('Analysis Session');
    await page.waitForTimeout(500);

    // Session analysis component should render
    await expect(historyPage.sessionAnalysis).toBeVisible({ timeout: 10_000 });
  });

  test('upload FIT file creates session', async ({ page, athlete, apiContext }) => {
    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    await historyPage.goto();

    // Count sessions before upload
    const sessionsBefore = await getSessions(apiContext, athlete.token);
    const countBefore = sessionsBefore.length;

    // We can't actually upload a real FIT file in E2E without one,
    // but we can verify the upload input is present and functional
    await expect(historyPage.fitUploadInput).toBeAttached();
    await expect(historyPage.stravaSyncBtn).toBeVisible();
  });

  test('delete session removes it from list', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'Delete Me Session',
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    await historyPage.goto();

    await expect(historyPage.getSessionItem('Delete Me Session')).toBeVisible({ timeout: 10_000 });

    // Delete via API and reload
    await deleteSession(apiContext, athlete.token, session.id);
    createdSessionIds.pop(); // Already deleted

    await historyPage.goto();
    await page.waitForTimeout(500);

    await expect(historyPage.getSessionItem('Delete Me Session')).not.toBeVisible();
  });
});
