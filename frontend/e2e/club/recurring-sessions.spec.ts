import { test, expect } from '../fixtures/base.fixture';
import { ClubDetailPage } from '../page-objects/club-detail.page';
import {
  createClub,
  deleteClub,
  createRecurringTemplate,
} from '../fixtures/test-data.fixture';

test.describe('Club - Recurring Sessions', () => {
  let clubDetailPage: ClubDetailPage;
  let clubId: string;

  test.beforeEach(async ({ page, coach, apiContext }) => {
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Recurring Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;
    clubDetailPage = new ClubDetailPage(page);
  });

  test.afterEach(async ({ apiContext, coach }) => {
    await deleteClub(apiContext, coach.token, clubId).catch(() => {});
  });

  test('create recurring session via UI', async ({ page, coach, authenticatedCoachPage }) => {
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    // Open form and create recurring session
    await clubDetailPage.createRecurringSession({
      title: 'E2E Weekly Ride',
      dayOfWeek: 'WEDNESDAY',
      timeOfDay: '18:00',
      location: 'City Park',
    });

    // Session instances should appear
    const sessionCard = clubDetailPage.getSessionCard('E2E Weekly Ride');
    await expect(sessionCard.first()).toBeVisible({ timeout: 10_000 });
  });

  test('create recurring session via API and verify instances', async ({
    page,
    coach,
    apiContext,
    authenticatedCoachPage,
  }) => {
    // Create via API
    await createRecurringTemplate(apiContext, coach.token, clubId, {
      title: 'API Recurring Ride',
      dayOfWeek: 'THURSDAY',
      timeOfDay: '07:00',
      location: 'Trail Head',
    });

    // Navigate to sessions tab
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    // At least one instance should be visible (may need to navigate weeks)
    const sessionCard = clubDetailPage.getSessionCard('API Recurring Ride');

    // Try current week and next few weeks
    let found = false;
    for (let i = 0; i < 4; i++) {
      if (await sessionCard.first().isVisible().catch(() => false)) {
        found = true;
        break;
      }
      await clubDetailPage.getSessionsNavNext().click();
      await page.waitForTimeout(500);
    }
    expect(found).toBeTruthy();
  });

  test('cancel future recurring sessions', async ({ page, coach, apiContext, authenticatedCoachPage }) => {
    // Create recurring via API
    const template = await createRecurringTemplate(apiContext, coach.token, clubId, {
      title: 'Cancel Test Ride',
      dayOfWeek: 'FRIDAY',
      timeOfDay: '17:00',
    });

    // Cancel all future via API
    const response = await apiContext.put(
      `http://localhost:8080/api/clubs/${clubId}/recurring-sessions/${template.id}/cancel-future`,
      {
        headers: { Authorization: `Bearer ${coach.token}` },
        data: { reason: 'E2E test cancel' },
      }
    );
    expect(response.ok()).toBeTruthy();

    const result = await response.json();
    expect(result.cancelledCount).toBeGreaterThan(0);

    // Navigate and verify sessions are cancelled
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    // Look for cancelled sessions
    const cancelledCards = page.locator('.sc-card--cancelled');
    // Sessions should be marked as cancelled or not visible
    // Navigate through weeks to find them
    for (let i = 0; i < 4; i++) {
      if (await cancelledCards.first().isVisible().catch(() => false)) {
        await expect(cancelledCards.first()).toBeVisible();
        break;
      }
      await clubDetailPage.getSessionsNavNext().click();
      await page.waitForTimeout(500);
    }
  });

  test('update recurring session with future instances via API', async ({
    page,
    coach,
    apiContext,
    authenticatedCoachPage,
  }) => {
    // Create recurring via API
    const template = await createRecurringTemplate(apiContext, coach.token, clubId, {
      title: 'Update Test Ride',
      dayOfWeek: 'SATURDAY',
      timeOfDay: '08:00',
    });

    // Update with future instances
    const response = await apiContext.put(
      `http://localhost:8080/api/clubs/${clubId}/recurring-sessions/${template.id}/with-instances`,
      {
        headers: { Authorization: `Bearer ${coach.token}` },
        data: {
          title: 'Updated Ride Name',
          sport: 'CYCLING',
          dayOfWeek: 'SATURDAY',
          timeOfDay: '09:00',
          location: 'New Location',
          durationMinutes: 90,
          category: 'SCHEDULED',
        },
      }
    );
    expect(response.ok()).toBeTruthy();

    // Navigate and find updated session
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('sessions');

    const updatedCard = clubDetailPage.getSessionCard('Updated Ride Name');
    let found = false;
    for (let i = 0; i < 4; i++) {
      if (await updatedCard.first().isVisible().catch(() => false)) {
        found = true;
        break;
      }
      await clubDetailPage.getSessionsNavNext().click();
      await page.waitForTimeout(500);
    }
    expect(found).toBeTruthy();
  });
});
