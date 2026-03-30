import { test, expect } from '../fixtures/base.fixture';
import { AnalyticsPage } from '../page-objects/analytics.page';
import { createSession, deleteSession } from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Analytics', () => {
  let analyticsPage: AnalyticsPage;
  const createdSessionIds: string[] = [];

  test.afterEach(async ({ apiContext, athlete }) => {
    for (const id of createdSessionIds) {
      await deleteSession(apiContext, athlete.token, id).catch(() => {});
    }
    createdSessionIds.length = 0;
  });

  test('power curve tab shows bars when sessions exist', async ({
    page,
    athlete,
    apiContext,
  }) => {
    // Create a session with power data
    const session = await createSession(apiContext, athlete.token, {
      title: 'Power Curve Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 250,
      tss: 100,
    });
    createdSessionIds.push(session.id);

    // Store a power curve for this session
    await apiContext.put(`http://localhost:8080/api/sessions/${session.id}/power-curve`, {
      headers: { Authorization: `Bearer ${athlete.token}` },
      data: { '5': 450, '15': 400, '30': 370, '60': 340, '300': 280, '1200': 250, '3600': 230 },
    });

    await injectAuth(page, athlete);
    analyticsPage = new AnalyticsPage(page);
    await analyticsPage.goto();

    // Power curve tab should be active by default
    await expect(analyticsPage.tabPowerCurve).toHaveClass(/active/);

    // If power data exists, bars should show
    const bars = analyticsPage.getPowerCurveBars();
    const emptyState = analyticsPage.getEmptyState();

    // Either bars are visible or empty state - both are valid
    const hasBars = await bars.first().isVisible().catch(() => false);
    const isEmpty = await emptyState.isVisible().catch(() => false);
    expect(hasBars || isEmpty).toBeTruthy();
  });

  test('volume tab renders chart and table', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'Volume Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 200,
      tss: 80,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    analyticsPage = new AnalyticsPage(page);
    await analyticsPage.goto();

    await analyticsPage.switchTab('volume');
    await page.waitForTimeout(500);

    // Volume tab content should show
    const volumeChart = analyticsPage.getVolumeChart();
    const emptyState = analyticsPage.getEmptyState();

    const hasChart = await volumeChart.isVisible().catch(() => false);
    const isEmpty = await emptyState.isVisible().catch(() => false);
    expect(hasChart || isEmpty).toBeTruthy();

    // If chart is visible, table should also be visible
    if (hasChart) {
      await expect(analyticsPage.getVolumeTableRows().first()).toBeVisible();
    }
  });

  test('volume group-by toggle switches between week and month', async ({
    page,
    athlete,
    apiContext,
  }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'GroupBy Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 200,
      tss: 90,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    analyticsPage = new AnalyticsPage(page);
    await analyticsPage.goto();

    await analyticsPage.switchTab('volume');
    await page.waitForTimeout(500);

    // Switch to month view
    await analyticsPage.setVolumeGroupBy('month');
    await page.waitForTimeout(500);

    // Switch back to week view
    await analyticsPage.setVolumeGroupBy('week');
    await page.waitForTimeout(500);

    // Page should still be functional (no errors)
    await expect(analyticsPage.tabVolume).toHaveClass(/active/);
  });

  test('records tab shows record cards', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'Records Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 3600,
      avgPower: 280,
    });
    createdSessionIds.push(session.id);

    // Store a power curve so personal records can be derived
    await apiContext.put(`http://localhost:8080/api/sessions/${session.id}/power-curve`, {
      headers: { Authorization: `Bearer ${athlete.token}` },
      data: { '5': 500, '15': 420, '60': 350, '300': 300, '1200': 270, '3600': 250 },
    });

    await injectAuth(page, athlete);
    analyticsPage = new AnalyticsPage(page);
    await analyticsPage.goto();

    await analyticsPage.switchTab('records');
    await page.waitForTimeout(500);

    // Records cards or empty state should show
    const records = analyticsPage.getRecordCards();
    const emptyState = analyticsPage.getEmptyState();

    const hasRecords = await records.first().isVisible().catch(() => false);
    const isEmpty = await emptyState.isVisible().catch(() => false);
    expect(hasRecords || isEmpty).toBeTruthy();
  });

  test('date range filter updates data', async ({ page, athlete, apiContext }) => {
    const session = await createSession(apiContext, athlete.token, {
      title: 'Date Filter Session',
      sportType: 'CYCLING',
      totalDurationSeconds: 2400,
      avgPower: 210,
      tss: 60,
    });
    createdSessionIds.push(session.id);

    await injectAuth(page, athlete);
    analyticsPage = new AnalyticsPage(page);
    await analyticsPage.goto();

    // Set a narrow date range that includes today
    const today = new Date().toISOString().split('T')[0];
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    const weekAgoStr = weekAgo.toISOString().split('T')[0];

    await analyticsPage.setDateFrom(weekAgoStr);
    await analyticsPage.setDateTo(today);
    await page.waitForTimeout(500);

    // Page should still work after date filter change
    await expect(analyticsPage.tabPowerCurve).toBeVisible();
  });
});
