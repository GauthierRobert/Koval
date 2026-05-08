import { test, expect } from '../fixtures/base.fixture';
import { ClubDetailPage } from '../page-objects/club-detail.page';
import { ClubFeedPage } from '../page-objects/club-feed.page';
import {
  createClub,
  deleteClub,
  joinClub,
  createAnnouncement,
  addComment,
  toggleEventReaction,
  createSpotlight,
  deleteSpotlight,
  getEngagementInsights,
  suggestMentions,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Club Engagement Toolkit', () => {
  let clubId: string;
  let clubDetail: ClubDetailPage;
  let feed: ClubFeedPage;

  test.beforeEach(async ({ apiContext, coach, athlete }) => {
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Engagement Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;
    // Athlete joins so members > 1 for spotlight + insights
    await joinClub(apiContext, athlete.token, clubId);
  });

  test.afterEach(async ({ apiContext, coach }) => {
    if (clubId) await deleteClub(apiContext, coach.token, clubId).catch(() => {});
  });

  // --- Reactions ---

  test('member adds emoji reaction to an announcement and count appears', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    const announcement = await createAnnouncement(
      apiContext,
      coach.token,
      clubId,
      'React on me!',
    );

    await injectAuth(page, athlete);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('feed');

    const event = page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: 'React on me!' })
      .first();
    await expect(event).toBeVisible({ timeout: 10_000 });

    await feed.toggleEventReaction('React on me!', 'fire');

    // The fire chip should now exist with a count of 1.
    const chip = feed.getEventReactionChip('React on me!', 'fire');
    await expect(chip).toBeVisible({ timeout: 5000 });
    await expect(chip).toContainText('1');
    await expect(chip).toHaveAttribute('aria-pressed', 'true');
  });

  test('reaction toggles off when clicked twice (chip disappears)', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    const announcement = await createAnnouncement(
      apiContext,
      coach.token,
      clubId,
      'Toggle me twice',
    );
    // Pre-react via API
    await toggleEventReaction(apiContext, athlete.token, clubId, announcement.id, 'fire');

    await injectAuth(page, athlete);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('feed');

    // Chip should be visible & active because we already reacted.
    let chip = feed.getEventReactionChip('Toggle me twice', 'fire');
    await expect(chip).toBeVisible({ timeout: 10_000 });
    await expect(chip).toHaveAttribute('aria-pressed', 'true');

    // Click again to remove.
    await chip.click();

    // Chip count should drop to 0 — and since picker is closed, the chip is hidden.
    await expect(chip).toBeHidden({ timeout: 5000 });
  });

  // --- Threaded replies ---

  test('coach posts a comment, member replies, reply renders nested under parent', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    const announcement = await createAnnouncement(
      apiContext,
      coach.token,
      clubId,
      'Discussion thread',
    );
    // Coach posts the parent comment.
    await addComment(apiContext, coach.token, clubId, announcement.id, 'Question for the group?');

    await injectAuth(page, athlete);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('feed');

    // Expand comments
    await feed.expandComments('Discussion thread');

    // Open the reply input on the parent comment and post a reply
    await feed.openReplyForFirstComment('Discussion thread');
    await feed.postReply('Discussion thread', 'Here is my answer');

    // Reply should appear inside the .reply-row container under the parent
    const event = page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: 'Discussion thread' })
      .first();
    const reply = event.locator('.reply-row').filter({ hasText: 'Here is my answer' });
    await expect(reply).toBeVisible({ timeout: 10_000 });

    // The reply should NOT have a "Reply" button (replies can't have replies — single-level rule)
    await expect(reply.locator('[data-testid="reply-btn"]')).toHaveCount(0);
  });

  // --- @mentions ---

  test('mention typeahead suggests active members and inserts an @name', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Sanity: API returns the athlete in suggestions.
    const suggestions = await suggestMentions(apiContext, coach.token, clubId, athlete.displayName.slice(0, 3));
    expect(suggestions.some((s) => s.userId === athlete.id)).toBeTruthy();

    await injectAuth(page, coach);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('feed');

    // Focus the composer (expands on focusin) and type "@E" to trigger typeahead.
    await feed.composerInput.click();
    await expect(feed.composer).toHaveClass(/composer--expanded/);
    await feed.composerInput.fill('Hi @E');
    const dropdown = page.locator('.mention-dropdown');
    await expect(dropdown).toBeVisible({ timeout: 5000 });

    // Click the first suggestion
    await dropdown.locator('.mention-suggestion').first().click();

    // The text should now contain the resolved @DisplayName
    await expect(feed.composerInput).toHaveValue(new RegExp(`@${athlete.displayName}`));

    // Submit and verify announcement renders the mention as a styled span
    await feed.postBtn.click();
    const event = page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: athlete.displayName })
      .first();
    await expect(event).toBeVisible({ timeout: 10_000 });
    await expect(event.locator('.mention').first()).toContainText(`@${athlete.displayName}`);
  });

  // --- Member spotlight ---

  test('coach creates a member spotlight via composer; spotlight card is pinned', async ({
    page,
    coach,
    athlete,
  }) => {
    await injectAuth(page, coach);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('feed');

    // Open composer + spotlight modal
    await feed.openSpotlightComposer();
    await expect(feed.spotlightComposer).toBeVisible({ timeout: 5000 });

    // Pick athlete + title and publish
    await feed.fillSpotlight({ userId: athlete.id, title: 'Sub-3 marathon!' });
    await feed.spotlightPublishBtn.click();

    // Spotlight card should appear in the pinned section with the title
    const pinned = page.locator('.pinned-section');
    await expect(pinned).toBeVisible({ timeout: 10_000 });
    const card = pinned
      .locator('app-feed-spotlight-card')
      .filter({ hasText: 'Sub-3 marathon!' });
    await expect(card).toBeVisible();
    await expect(card.locator('.spotlight-name')).toContainText(athlete.displayName);
  });

  test('member cannot see spotlight composer button', async ({
    page,
    athlete,
  }) => {
    await injectAuth(page, athlete);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('feed');

    // Composer is coach/admin only — for an athlete the entire announcement composer is hidden,
    // so the spotlight-open button must not exist on the page.
    await expect(feed.spotlightOpenBtn).toHaveCount(0);
  });

  test('coach deletes a spotlight from the card', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    const spotlight = await createSpotlight(apiContext, coach.token, clubId, {
      spotlightedUserId: athlete.id,
      title: 'Comeback story',
      message: 'Welcome back',
      badge: 'COMEBACK',
      expiresInDays: 7,
    });
    expect(spotlight.id).toBeTruthy();

    await injectAuth(page, coach);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('feed');

    const card = page
      .locator('app-feed-spotlight-card')
      .filter({ hasText: 'Comeback story' });
    await expect(card).toBeVisible({ timeout: 10_000 });

    page.once('dialog', (d) => d.accept());
    await card.locator('.spotlight-action-link--danger').click();

    await expect(card).toHaveCount(0, { timeout: 5000 });
  });

  // --- Engagement insights panel ---

  test('coach sees engagement insights panel under Members tab; member does not', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Generate some engagement signal: an announcement, a comment, a reaction.
    const announcement = await createAnnouncement(
      apiContext,
      coach.token,
      clubId,
      'Insights seed',
    );
    await addComment(apiContext, athlete.token, clubId, announcement.id, 'a comment');
    await toggleEventReaction(apiContext, athlete.token, clubId, announcement.id, 'clap');

    // Sanity: API returns rows for both members.
    const insights = await getEngagementInsights(apiContext, coach.token, clubId, 30);
    expect(insights.members.length).toBeGreaterThanOrEqual(2);

    // Coach: panel visible, has rows.
    await injectAuth(page, coach);
    clubDetail = new ClubDetailPage(page);
    feed = new ClubFeedPage(page);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('members');
    await expect(feed.engagementInsightsPanel).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('[data-testid="insights-row"]').first()).toBeVisible();

    // Member: panel NOT rendered.
    await page.context().clearCookies();
    await injectAuth(page, athlete);
    await clubDetail.goto(clubId);
    await clubDetail.switchTab('members');
    await expect(feed.engagementInsightsPanel).toHaveCount(0);
  });
});
