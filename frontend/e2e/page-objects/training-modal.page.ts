import { type Page, type Locator } from '@playwright/test';

export class TrainingModalPage {
  readonly page: Page;
  readonly modal: Locator;
  readonly closeBtn: Locator;
  readonly tabAi: Locator;
  readonly tabSelect: Locator;
  readonly searchInput: Locator;
  readonly datePicker: Locator;
  readonly notesTextarea: Locator;
  readonly cancelBtn: Locator;
  readonly submitBtn: Locator;
  readonly errorMessage: Locator;
  readonly successMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.modal = page.locator('.modal-panel');
    this.closeBtn = page.locator('.modal-panel .close-btn');
    this.tabAi = page.locator('[data-testid="tab-ai"]');
    this.tabSelect = page.locator('[data-testid="tab-select"]');
    this.searchInput = page.locator('.modal-panel .search-input');
    this.datePicker = page.locator('[data-testid="form-date-picker"]');
    this.notesTextarea = page.locator('.modal-panel .prompt-textarea').last();
    this.cancelBtn = page.locator('.modal-panel .btn-ghost');
    this.submitBtn = page.locator('.modal-panel .btn-primary');
    this.errorMessage = page.locator('.status-message.error');
    this.successMessage = page.locator('.status-message.success');
  }

  async switchToSelectTab() {
    await this.tabSelect.click();
    await this.page.waitForTimeout(200);
  }

  async switchToAiTab() {
    await this.tabAi.click();
    await this.page.waitForTimeout(200);
  }

  getTrainingItems() {
    return this.page.locator('.training-item');
  }

  getTrainingItem(title: string) {
    return this.page.locator('.training-item').filter({ hasText: title });
  }

  async selectTraining(title: string) {
    await this.getTrainingItem(title).click();
  }

  async setDate(date: string) {
    await this.datePicker.fill(date);
  }

  async setNotes(notes: string) {
    await this.notesTextarea.fill(notes);
  }

  async submit() {
    await this.submitBtn.click();
    await this.page.waitForTimeout(500);
  }

  async close() {
    await this.closeBtn.click();
  }

  // Athlete checkboxes (group-assign mode)
  getAthleteCheckboxes() {
    return this.page.locator('.checkbox-label');
  }

  getAthleteCheckbox(name: string) {
    return this.page.locator('.checkbox-label').filter({ hasText: name }).locator('input');
  }

  async toggleAthlete(name: string) {
    await this.getAthleteCheckbox(name).click();
  }

  // Tag filter
  getTagFilterChips() {
    return this.page.locator('.modal-tag-chip');
  }

  async toggleTagFilter(tagName: string) {
    await this.page.locator('.modal-tag-chip').filter({ hasText: tagName }).click();
  }

  isVisible() {
    return this.modal.isVisible();
  }
}
