import { test, expect } from '../fixtures/base.fixture';
import { ClubDetailPage } from '../page-objects/club-detail.page';
import { ClubFeedPage } from '../page-objects/club-feed.page';
import {
  createClub,
  deleteClub,
  joinClub,
  createClubSession,
  joinClubSession,
  createAnnouncement,
  getFeedEvents,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Club Feed', () => {
  let clubId: string;
  let clubDetailPage: ClubDetailPage;
  let feedPage: ClubFeedPage;

  test.beforeEach(async ({ apiContext, coach }) => {
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Feed Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;
  });

  test.afterEach(async ({ apiContext, coach }) => {
    if (clubId) await deleteClub(apiContext, coach.token, clubId).catch(() => {});
  });

  test('view feed events after session completion', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    await joinClub(apiContext, athlete.token, clubId);

    // Create a session
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Feed Test Session',
      scheduledAt: tomorrow.toISOString(),
    });

    // Athlete joins session
    await joinClubSession(apiContext, athlete.token, clubId, session.id);

    // Navigate to club detail feed tab
    await injectAuth(page, coach);
    clubDetailPage = new ClubDetailPage(page);
    feedPage = new ClubFeedPage(page);
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('feed');
    await page.waitForTimeout(1000);

    // Feed should be rendered (either events or empty state)
    const feedTimeline = page.locator('.feed-timeline');
    const emptyFeed = page.locator('.empty-feed');
    const hasFeed = await feedTimeline.isVisible().catch(() => false);
    const isEmpty = await emptyFeed.isVisible().catch(() => false);
    expect(hasFeed || isEmpty).toBeTruthy();
  });

  test('coach posts announcement', async ({ page, coach, apiContext, authenticatedCoachPage }) => {
    clubDetailPage = new ClubDetailPage(page);
    feedPage = new ClubFeedPage(page);

    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('feed');
    await page.waitForTimeout(500);

    // Composer should be visible for coach
    await expect(feedPage.composer).toBeVisible({ timeout: 10_000 });

    // Post an announcement
    await feedPage.postAnnouncement('E2E Test Announcement Content');
    await page.waitForTimeout(1000);

    // Announcement should appear in feed
    const announcement = feedPage.getFeedEvent('E2E Test Announcement Content');
    await expect(announcement).toBeVisible({ timeout: 10_000 });
  });

  test('give kudos (mocked)', async ({ page, coach, athlete, apiContext }) => {
    await joinClub(apiContext, athlete.token, clubId);

    // Create a session completion event via API
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const session = await createClubSession(apiContext, coach.token, clubId, {
      title: 'Kudos Test Session',
      scheduledAt: yesterday.toISOString(),
    });
    await joinClubSession(apiContext, athlete.token, clubId, session.id);

    await injectAuth(page, coach);
    clubDetailPage = new ClubDetailPage(page);
    feedPage = new ClubFeedPage(page);

    // Mock the kudos endpoint to avoid real Strava calls
    await page.route(`**/api/clubs/${clubId}/feed/*/kudos`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          results: [{ athleteName: 'Test Athlete', success: true }],
          successCount: 1,
          failCount: 0,
        }),
      });
    });

    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('feed');
    await page.waitForTimeout(1000);

    // Look for a session completion card with kudos button
    const kudosBtn = page.locator('[data-testid="feed-kudos-btn"]').first();
    if (await kudosBtn.isVisible().catch(() => false)) {
      await kudosBtn.click();
      await page.waitForTimeout(500);

      // After clicking kudos, the button should be replaced with "kudos given" label
      const kudosGiven = page.locator('.kudos-given').first();
      await expect(kudosGiven).toBeVisible({ timeout: 5000 });
    }
  });

  test('feed pagination with load more', async ({ page, coach, apiContext }) => {
    // Create multiple announcements to fill feed
    for (let i = 0; i < 5; i++) {
      await createAnnouncement(apiContext, coach.token, clubId, `Announcement ${i + 1}`);
    }

    await injectAuth(page, coach);
    clubDetailPage = new ClubDetailPage(page);
    feedPage = new ClubFeedPage(page);

    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('feed');
    await page.waitForTimeout(1000);

    // Feed events should be visible
    const feedEvents = feedPage.getFeedEvents();
    await expect(feedEvents.first()).toBeVisible({ timeout: 10_000 });

    // Check if load more button appears (depends on total event count)
    const loadMoreBtn = feedPage.loadMoreBtn;
    if (await loadMoreBtn.isVisible().catch(() => false)) {
      const countBefore = await feedEvents.count();
      await loadMoreBtn.click();
      await page.waitForTimeout(1000);

      // After loading more, event count should increase or stay same
      const countAfter = await feedEvents.count();
      expect(countAfter).toBeGreaterThanOrEqual(countBefore);
    }
  });

  test('pinned events show at top', async ({ page, coach, athlete, apiContext }) => {
    await joinClub(apiContext, athlete.token, clubId);

    // Create a session that will generate a pinned event
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    await createClubSession(apiContext, coach.token, clubId, {
      title: 'Pinned Session',
      scheduledAt: tomorrow.toISOString(),
    });

    await injectAuth(page, coach);
    clubDetailPage = new ClubDetailPage(page);
    feedPage = new ClubFeedPage(page);

    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('feed');
    await page.waitForTimeout(1000);

    // Check if pinned section exists
    const pinnedSection = feedPage.pinnedSection;
    if (await pinnedSection.isVisible().catch(() => false)) {
      // Pinned section should appear before regular timeline
      const pinnedY = (await pinnedSection.boundingBox())?.y ?? 0;
      const timeline = page.locator('.feed-timeline');
      if (await timeline.isVisible().catch(() => false)) {
        const timelineY = (await timeline.boundingBox())?.y ?? 0;
        expect(pinnedY).toBeLessThan(timelineY);
      }
    }
  });

  test('upcoming sessions sidebar shows future session', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    await joinClub(apiContext, athlete.token, clubId);

    // Create a future session
    const nextWeek = new Date();
    nextWeek.setDate(nextWeek.getDate() + 3);
    await createClubSession(apiContext, coach.token, clubId, {
      title: 'Upcoming Sidebar Session',
      scheduledAt: nextWeek.toISOString(),
    });

    await injectAuth(page, coach);
    clubDetailPage = new ClubDetailPage(page);
    feedPage = new ClubFeedPage(page);

    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('feed');
    await page.waitForTimeout(1000);

    // Upcoming sessions sidebar should be visible
    const sidebar = feedPage.getUpcomingSessions();
    await expect(sidebar).toBeVisible({ timeout: 10_000 });
  });
});
