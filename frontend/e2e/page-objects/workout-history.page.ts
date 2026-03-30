import { type Page, type Locator } from '@playwright/test';

export class WorkoutHistoryPage {
  readonly page: Page;
  readonly stravaSyncBtn: Locator;
  readonly fitUploadInput: Locator;
  readonly dateFromInput: Locator;
  readonly dateToInput: Locator;
  readonly sessionAnalysis: Locator;

  constructor(page: Page) {
    this.page = page;
    this.stravaSyncBtn = page.locator('[data-testid="strava-sync-btn"]');
    this.fitUploadInput = page.locator('[data-testid="fit-upload-input"]');
    this.dateFromInput = page.locator('[data-testid="date-from"]');
    this.dateToInput = page.locator('[data-testid="date-to"]');
    this.sessionAnalysis = page.locator('app-session-analysis');
  }

  async goto() {
    await this.page.goto('/history');
    await this.page.waitForLoadState('networkidle');
  }

  getSessionItems(): Locator {
    return this.page.locator('[data-testid="session-item"]');
  }

  getSessionItem(title: string): Locator {
    return this.page.locator('[data-testid="session-item"]').filter({ hasText: title });
  }

  async selectSession(title: string) {
    await this.getSessionItem(title).click();
  }

  getSportFilterPills(): Locator {
    return this.page.locator('app-filter-pills button');
  }

  async filterBySport(sport: string) {
    await this.page.locator('app-filter-pills button').filter({ hasText: sport }).click();
  }

  async setDateFrom(date: string) {
    await this.dateFromInput.fill(date);
    await this.dateFromInput.dispatchEvent('change');
  }

  async setDateTo(date: string) {
    await this.dateToInput.fill(date);
    await this.dateToInput.dispatchEvent('change');
  }

  async uploadFitFile(filePath: string) {
    await this.fitUploadInput.setInputFiles(filePath);
  }

  getDeleteBtn(): Locator {
    return this.page.locator('.dl-btn');
  }
}
