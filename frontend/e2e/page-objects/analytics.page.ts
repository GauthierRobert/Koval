import { type Page, type Locator } from '@playwright/test';

export class AnalyticsPage {
  readonly page: Page;
  readonly tabPowerCurve: Locator;
  readonly tabVolume: Locator;
  readonly tabRecords: Locator;
  readonly dateFromInput: Locator;
  readonly dateToInput: Locator;
  readonly volumeGroupBy: Locator;

  constructor(page: Page) {
    this.page = page;
    this.tabPowerCurve = page.locator('[data-testid="tab-power-curve"]');
    this.tabVolume = page.locator('[data-testid="tab-volume"]');
    this.tabRecords = page.locator('[data-testid="tab-records"]');
    this.dateFromInput = page.locator('[data-testid="analytics-date-from"]');
    this.dateToInput = page.locator('[data-testid="analytics-date-to"]');
    this.volumeGroupBy = page.locator('[data-testid="volume-group-by"]');
  }

  async goto() {
    await this.page.goto('/analytics');
    await this.page.waitForLoadState('networkidle');
  }

  async switchTab(tab: 'power-curve' | 'volume' | 'records') {
    const tabLocator = {
      'power-curve': this.tabPowerCurve,
      volume: this.tabVolume,
      records: this.tabRecords,
    }[tab];
    await tabLocator.click();
  }

  async setDateFrom(date: string) {
    await this.dateFromInput.fill(date);
    await this.dateFromInput.dispatchEvent('change');
  }

  async setDateTo(date: string) {
    await this.dateToInput.fill(date);
    await this.dateToInput.dispatchEvent('change');
  }

  getPowerCurveChart(): Locator {
    return this.page.locator('.bar-chart').first();
  }

  getPowerCurveBars(): Locator {
    return this.page.locator('.bar-chart .bar-col');
  }

  getVolumeChart(): Locator {
    return this.page.locator('.volume-chart');
  }

  async setVolumeGroupBy(value: 'week' | 'month') {
    await this.volumeGroupBy.selectOption(value);
  }

  getVolumeTableRows(): Locator {
    return this.page.locator('.volume-table tbody tr');
  }

  getRecordCards(): Locator {
    return this.page.locator('[data-testid="record-card"]');
  }

  getEmptyState(): Locator {
    return this.page.locator('.empty-state');
  }
}
