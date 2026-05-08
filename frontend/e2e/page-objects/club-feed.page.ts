import { type Page, type Locator } from '@playwright/test';

export class ClubFeedPage {
  readonly page: Page;
  readonly composer: Locator;
  readonly composerInput: Locator;
  readonly postBtn: Locator;
  readonly loadMoreBtn: Locator;
  readonly pinnedSection: Locator;
  readonly spotlightOpenBtn: Locator;
  readonly spotlightComposer: Locator;
  readonly spotlightMemberSelect: Locator;
  readonly spotlightTitleInput: Locator;
  readonly spotlightPublishBtn: Locator;
  readonly engagementInsightsPanel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.composer = page.locator('[data-testid="feed-composer"]');
    this.composerInput = page.locator('[data-testid="feed-composer-input"]');
    this.postBtn = page.locator('[data-testid="feed-post-btn"]');
    this.loadMoreBtn = page.locator('[data-testid="feed-load-more"]');
    this.pinnedSection = page.locator('.pinned-section');
    this.spotlightOpenBtn = page.locator('[data-testid="spotlight-open-btn"]');
    this.spotlightComposer = page.locator('[data-testid="spotlight-composer"]');
    this.spotlightMemberSelect = page.locator('[data-testid="spotlight-member-select"]');
    this.spotlightTitleInput = page.locator('[data-testid="spotlight-title-input"]');
    this.spotlightPublishBtn = page.locator('[data-testid="spotlight-publish-btn"]');
    this.engagementInsightsPanel = page.locator('[data-testid="engagement-insights-panel"]');
  }

  getFeedEvents(): Locator {
    return this.page.locator('[data-testid="feed-event"]');
  }

  getFeedEvent(text: string): Locator {
    return this.page.locator('[data-testid="feed-event"]').filter({ hasText: text });
  }

  getAnnouncementCards(): Locator {
    return this.page.locator('app-feed-announcement-card');
  }

  getSessionCompletionCards(): Locator {
    return this.page.locator('app-feed-session-completion-card');
  }

  async expandComposer() {
    await this.composerInput.focus();
  }

  async postAnnouncement(text: string) {
    await this.composerInput.focus();
    await this.composerInput.fill(text);
    await this.postBtn.click();
  }

  getKudosBtn(eventText: string): Locator {
    return this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .locator('[data-testid="feed-kudos-btn"]');
  }

  getKudosGivenLabel(eventText: string): Locator {
    return this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .locator('.kudos-given');
  }

  getUpcomingSessions(): Locator {
    return this.page.locator('app-feed-sessions-upcoming');
  }

  // --- Engagement helpers (reactions, replies, spotlights) ---

  getSpotlightCards(): Locator {
    return this.page.locator('app-feed-spotlight-card');
  }

  /** Get the reaction chip button for a given emoji on the first feed event matching `eventText`. */
  getEventReactionChip(eventText: string, emoji: string): Locator {
    return this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .first()
      .locator(`[data-testid="reaction-chip-${emoji}"]`);
  }

  /** Click the floating "+ reaction" button on a feed event to expose the picker. */
  async openReactionPicker(eventText: string): Promise<void> {
    await this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .first()
      .locator('[data-testid="reaction-bar"] [data-testid="reaction-add"]')
      .first()
      .click();
  }

  async toggleEventReaction(eventText: string, emoji: string): Promise<void> {
    await this.openReactionPicker(eventText);
    await this.getEventReactionChip(eventText, emoji).click();
  }

  /** Expand the comments section under the first matching feed event. */
  async expandComments(eventText: string): Promise<void> {
    await this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .first()
      .locator('[data-testid="comments-toggle"]')
      .first()
      .click();
  }

  async postComment(eventText: string, content: string): Promise<void> {
    const event = this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .first();
    await event.locator('[data-testid="comment-input"]').fill(content);
    await event.locator('[data-testid="comment-post-btn"]').click();
  }

  async openReplyForFirstComment(eventText: string): Promise<void> {
    await this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .first()
      .locator('[data-testid="reply-btn"]')
      .first()
      .click();
  }

  async postReply(eventText: string, content: string): Promise<void> {
    const event = this.page
      .locator('[data-testid="feed-event"]')
      .filter({ hasText: eventText })
      .first();
    await event.locator('[data-testid="reply-input"]').fill(content);
    await event.locator('[data-testid="reply-post-btn"]').click();
  }

  async openSpotlightComposer(): Promise<void> {
    await this.composerInput.focus();
    await this.spotlightOpenBtn.click();
  }

  async fillSpotlight(opts: { userId: string; title: string }): Promise<void> {
    await this.spotlightMemberSelect.selectOption(opts.userId);
    await this.spotlightTitleInput.fill(opts.title);
  }
}
