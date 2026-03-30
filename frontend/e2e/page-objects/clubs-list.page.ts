import { type Page, type Locator } from '@playwright/test';

export class ClubsListPage {
  readonly page: Page;
  readonly createClubBtn: Locator;
  readonly yourClubsSection: Locator;
  readonly browsePublicSection: Locator;

  // Create modal
  readonly modal: Locator;
  readonly nameInput: Locator;
  readonly descriptionInput: Locator;
  readonly locationInput: Locator;
  readonly visibilitySelect: Locator;
  readonly cancelBtn: Locator;
  readonly submitBtn: Locator;

  constructor(page: Page) {
    this.page = page;
    this.createClubBtn = page.locator('[data-testid="create-club-btn"]');
    this.yourClubsSection = page.locator('[data-testid="your-clubs-section"]');
    this.browsePublicSection = page.locator('[data-testid="browse-public-section"]');

    this.modal = page.locator('[data-testid="create-club-modal"]');
    this.nameInput = page.locator('[data-testid="form-club-name"]');
    this.descriptionInput = page.locator('[data-testid="form-club-description"]');
    this.locationInput = page.locator('[data-testid="form-club-location"]');
    this.visibilitySelect = page.locator('[data-testid="form-club-visibility"]');
    this.cancelBtn = this.modal.locator('.btn-secondary');
    this.submitBtn = this.modal.locator('.btn-primary');
  }

  async goto() {
    await this.page.goto('/clubs');
    await this.page.waitForLoadState('networkidle');
  }

  getClubCards() {
    return this.page.locator('[data-testid="club-card"]');
  }

  getClubCard(name: string) {
    return this.page.locator('[data-testid="club-card"]').filter({ hasText: name });
  }

  async createClub(name: string, description?: string, visibility?: 'PUBLIC' | 'PRIVATE') {
    await this.createClubBtn.click();
    await this.nameInput.fill(name);
    if (description) {
      await this.descriptionInput.fill(description);
    }
    if (visibility) {
      await this.visibilitySelect.selectOption(visibility);
    }
    await this.submitBtn.click();
    await this.page.waitForTimeout(500);
  }

  getJoinBtn(clubName: string) {
    return this.getClubCard(clubName).locator('[data-testid="join-btn"]');
  }

  getLeaveBtn(clubName: string) {
    return this.getClubCard(clubName).locator('[data-testid="leave-btn"]');
  }

  async openClubDetail(clubName: string) {
    await this.getClubCard(clubName).click();
  }
}
