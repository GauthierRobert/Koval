import { type Page, type Locator } from '@playwright/test';

export class SessionAnalysisPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  getStatCards(): Locator {
    return this.page.locator('[data-testid="stat-card"]');
  }

  getStatValue(label: string): Locator {
    return this.page
      .locator('[data-testid="stat-card"]')
      .filter({ hasText: label })
      .locator('.stat-value');
  }

  getRpeSlider(): Locator {
    return this.page.locator('.rpe-slider');
  }

  getRpeTicks(): Locator {
    return this.page.locator('[data-testid="rpe-tick"]');
  }

  async setRpe(value: number) {
    await this.page
      .locator('[data-testid="rpe-tick"]')
      .filter({ hasText: String(value) })
      .click();
  }

  getZoneDistribution(): Locator {
    return this.page.locator('.zone-distribution');
  }

  getZoneRows(): Locator {
    return this.page.locator('[data-testid="zone-row"]');
  }

  getZonePicker(): Locator {
    return this.page.locator('[data-testid="zone-picker"]');
  }

  async selectZoneSystem(name: string) {
    await this.getZonePicker().selectOption({ label: name });
  }

  getBlocksSection(): Locator {
    return this.page.locator('.blocks-section');
  }

  getBlockViewToggles(): Locator {
    return this.page.locator('[data-testid="block-view-toggle"]');
  }

  async toggleBlockView(view: 'planned' | 'interpolated') {
    const label = view === 'planned' ? 'Planned' : 'Interpolated';
    await this.page
      .locator('[data-testid="block-view-toggle"]')
      .filter({ hasText: new RegExp(label, 'i') })
      .click();
  }
}
