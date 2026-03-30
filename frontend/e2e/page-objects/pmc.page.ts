import { type Page, type Locator } from '@playwright/test';

export class PmcPage {
  readonly page: Page;
  readonly ctlBadge: Locator;
  readonly atlBadge: Locator;
  readonly tsbBadge: Locator;
  readonly refreshBtn: Locator;
  readonly pmcChart: Locator;
  readonly schedulePill: Locator;
  readonly peakResult: Locator;

  constructor(page: Page) {
    this.page = page;
    this.ctlBadge = page.locator('[data-testid="pmc-ctl-badge"]');
    this.atlBadge = page.locator('[data-testid="pmc-atl-badge"]');
    this.tsbBadge = page.locator('[data-testid="pmc-tsb-badge"]');
    this.refreshBtn = page.locator('[data-testid="pmc-refresh-btn"]');
    this.pmcChart = page.locator('[data-testid="pmc-chart"]');
    this.schedulePill = page.locator('.schedule-pill');
    this.peakResult = page.locator('.peak-result');
  }

  async goto() {
    await this.page.goto('/pmc');
    await this.page.waitForLoadState('networkidle');
  }

  async refresh() {
    await this.refreshBtn.click();
  }

  getCtlValue(): Locator {
    return this.ctlBadge.locator('.badge-value');
  }

  getAtlValue(): Locator {
    return this.atlBadge.locator('.badge-value');
  }

  getTsbValue(): Locator {
    return this.tsbBadge.locator('.badge-value');
  }
}
