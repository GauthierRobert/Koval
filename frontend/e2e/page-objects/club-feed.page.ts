import { type Page, type Locator } from '@playwright/test';

export class ClubFeedPage {
  readonly page: Page;
  readonly composer: Locator;
  readonly composerInput: Locator;
  readonly postBtn: Locator;
  readonly loadMoreBtn: Locator;
  readonly pinnedSection: Locator;

  constructor(page: Page) {
    this.page = page;
    this.composer = page.locator('[data-testid="feed-composer"]');
    this.composerInput = page.locator('[data-testid="feed-composer-input"]');
    this.postBtn = page.locator('[data-testid="feed-post-btn"]');
    this.loadMoreBtn = page.locator('[data-testid="feed-load-more"]');
    this.pinnedSection = page.locator('.pinned-section');
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
}
