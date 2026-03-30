import { type Page, type Locator } from '@playwright/test';

export class CalendarPage {
  readonly page: Page;
  readonly weekViewBtn: Locator;
  readonly monthViewBtn: Locator;
  readonly navPrev: Locator;
  readonly navToday: Locator;
  readonly navNext: Locator;
  readonly prefsBtn: Locator;

  constructor(page: Page) {
    this.page = page;
    this.weekViewBtn = page.locator('[data-testid="view-selector-week"]');
    this.monthViewBtn = page.locator('[data-testid="view-selector-month"]');
    this.navPrev = page.locator('[data-testid="nav-btn-prev"]');
    this.navToday = page.locator('[data-testid="nav-btn-today"]');
    this.navNext = page.locator('[data-testid="nav-btn-next"]');
    this.prefsBtn = page.locator('[data-testid="prefs-btn"]');
  }

  async goto() {
    await this.page.goto('/calendar');
    await this.page.waitForLoadState('networkidle');
  }

  // Week view elements
  getDayColumns() {
    return this.page.locator('.day-column');
  }

  getWorkoutCards() {
    return this.page.locator('.workout-card');
  }

  getWorkoutCard(title: string) {
    return this.page.locator('.workout-card').filter({ hasText: title });
  }

  getClubSessionCards() {
    return this.page.locator('.workout-card--club-session');
  }

  getClubSessionCard(title: string) {
    return this.page.locator('.workout-card--club-session').filter({ hasText: title });
  }

  // Club session actions
  async joinClubSession(title: string) {
    const card = this.getClubSessionCard(title);
    await card.locator('[data-testid="btn-join-club-session"]').click();
    await this.page.waitForTimeout(300);
  }

  async cancelClubSession(title: string) {
    const card = this.getClubSessionCard(title);
    await card.locator('[data-testid="btn-cancel-club-session"]').click();
    await this.page.waitForTimeout(300);
  }

  // Workout actions
  async completeWorkout(title: string) {
    const card = this.getWorkoutCard(title);
    await card.locator('[data-testid="btn-complete"]').click();
    await this.page.waitForTimeout(300);
  }

  async skipWorkout(title: string) {
    const card = this.getWorkoutCard(title);
    await card.locator('[data-testid="btn-skip"]').click();
    await this.page.waitForTimeout(300);
  }

  async deleteWorkout(title: string) {
    const card = this.getWorkoutCard(title);
    await card.locator('[data-testid="btn-delete"]').click();
    await this.page.waitForTimeout(300);
  }

  getWorkoutStatus(title: string) {
    return this.getWorkoutCard(title).locator('.status-badge');
  }

  // Month view elements
  getMonthCells() {
    return this.page.locator('.month-cell');
  }

  getMiniCards() {
    return this.page.locator('.mini-card');
  }

  getMiniCard(title: string) {
    return this.page.locator('.mini-card').filter({ hasText: title });
  }

  // Add training
  getAddBtn(date: string) {
    return this.page.locator(`[data-testid="add-training-btn-${date}"]`);
  }

  // Preferences
  async openPrefs() {
    await this.prefsBtn.click();
  }

  getClubVisibilityToggle(clubName: string) {
    return this.page.locator('[data-testid="prefs-dropdown"]').locator('.prefs-club-row').filter({ hasText: clubName }).locator('input');
  }
}
