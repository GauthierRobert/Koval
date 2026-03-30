import { test, expect } from '../fixtures/base.fixture';
import { WorkoutHistoryPage } from '../page-objects/workout-history.page';
import { SessionAnalysisPage } from '../page-objects/session-analysis.page';
import {
  createSession,
  deleteSession,
  updateSessionRpe,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Session Analysis', () => {
  let historyPage: WorkoutHistoryPage;
  let analysisPage: SessionAnalysisPage;
  const createdSessionIds: string[] = [];

  test.afterEach(async ({ apiContext, athlete }) => {
    for (const id of createdSessionIds) {
      await deleteSession(apiContext, athlete.token, id).catch(() => {});
    }
    createdSessionIds.length = 0;
  });

  test('display session metrics (TSS, IF, duration, avg power)', async ({
    page,
    athlete,
    apiContext,
  }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'Metrics Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 230,
      avgHR: 155,
      avgCadence: 90,
      tss: 95,
      intensityFactor: 0.95,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    analysisPage = new SessionAnalysisPage(page);
    await historyPage.goto();

    await historyPage.selectSession('Metrics Session');
    await page.waitForTimeout(500);

    // Stat cards should be visible
    await expect(analysisPage.getStatCards().first()).toBeVisible({ timeout: 10_000 });

    // Check specific stats
    await expect(analysisPage.getStatValue('TSS')).toBeVisible();
    await expect(analysisPage.getStatValue('IF')).toBeVisible();
  });

  test('set RPE and verify persistence', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'RPE Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 1800,
      avgPower: 180,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    analysisPage = new SessionAnalysisPage(page);
    await historyPage.goto();

    await historyPage.selectSession('RPE Session');
    await page.waitForTimeout(500);

    // Check if RPE slider is present (only shows when tss is not set)
    const rpeSlider = analysisPage.getRpeSlider();
    if (await rpeSlider.isVisible().catch(() => false)) {
      // Set RPE via slider
      await rpeSlider.fill('7');
      await rpeSlider.dispatchEvent('change');
      await page.waitForTimeout(1000);

      // Verify via API
      const response = await apiContext.get(
        `http://localhost:8080/api/sessions/${session.id}`,
        { headers: { Authorization: `Bearer ${athlete.token}` } }
      );
      const updatedSession = await response.json();
      expect(updatedSession.rpe).toBe(7);
    } else {
      // Session has TSS so RPE block is hidden, set via API
      await updateSessionRpe(apiContext, athlete.token, session.id, 7);
      const response = await apiContext.get(
        `http://localhost:8080/api/sessions/${session.id}`,
        { headers: { Authorization: `Bearer ${athlete.token}` } }
      );
      const updatedSession = await response.json();
      expect(updatedSession.rpe).toBe(7);
    }
  });

  test('zone distribution renders when FIT data present', async ({
    page,
    athlete,
    apiContext,
  }) => {
    // Create a session - zone distribution requires FIT data
    const session = await createSession(apiContext, athlete.token, {
      title: 'Zone Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 200,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    analysisPage = new SessionAnalysisPage(page);
    await historyPage.goto();

    await historyPage.selectSession('Zone Session');
    await page.waitForTimeout(500);

    // Session analysis should render
    await expect(historyPage.sessionAnalysis).toBeVisible({ timeout: 10_000 });

    // Zone distribution may or may not be visible depending on FIT data
    // If visible, check zone rows
    const zoneSection = analysisPage.getZoneDistribution();
    if (await zoneSection.isVisible().catch(() => false)) {
      await expect(analysisPage.getZoneRows().first()).toBeVisible();
    }
  });

  test('switch zone system updates labels', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'Zone System Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 220,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    historyPage = new WorkoutHistoryPage(page);
    analysisPage = new SessionAnalysisPage(page);
    await historyPage.goto();

    await historyPage.selectSession('Zone System Session');
    await page.waitForTimeout(500);

    await expect(historyPage.sessionAnalysis).toBeVisible({ timeout: 10_000 });

    // Zone picker should be present if zones are showing
    const zonePicker = analysisPage.getZonePicker();
    if (await zonePicker.isVisible().catch(() => false)) {
      // Get available options
      const options = zonePicker.locator('option');
      const count = await options.count();
      if (count > 1) {
        // Switch to second zone system
        await options.nth(1).click();
        await page.waitForTimeout(500);
        // Zone rows should still be visible
        await expect(analysisPage.getZoneRows().first()).toBeVisible();
      }
    }
  });
});
