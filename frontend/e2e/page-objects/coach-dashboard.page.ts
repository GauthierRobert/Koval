import { type Page, type Locator } from '@playwright/test';

export class CoachDashboardPage {
  readonly page: Page;

  // Sidebar
  readonly athleteList: Locator;
  readonly tagFilterAll: Locator;

  // Header
  readonly shareBtn: Locator;

  // Tabs
  readonly tabPerformance: Locator;
  readonly tabPhysiology: Locator;
  readonly tabHistory: Locator;
  readonly tabPmc: Locator;
  readonly tabGoals: Locator;

  // Schedule
  readonly scheduleTable: Locator;
  readonly scheduleWeekPrev: Locator;
  readonly scheduleWeekNext: Locator;

  // Modals
  readonly inviteCodeModal: Locator;

  constructor(page: Page) {
    this.page = page;
    this.athleteList = page.locator('[data-testid="athlete-list"]');
    this.tagFilterAll = page.locator('[data-testid="tag-filter-all"]');
    this.shareBtn = page.locator('[data-testid="share-btn"]');
    this.tabPerformance = page.locator('[data-testid="tab-performance"]');
    this.tabPhysiology = page.locator('[data-testid="tab-physiology"]');
    this.tabHistory = page.locator('[data-testid="tab-history"]');
    this.tabPmc = page.locator('[data-testid="tab-pmc"]');
    this.tabGoals = page.locator('[data-testid="tab-goals"]');
    this.scheduleTable = page.locator('[data-testid="schedule-table"]');
    this.scheduleWeekPrev = page.locator('[data-testid="schedule-week-prev"]');
    this.scheduleWeekNext = page.locator('[data-testid="schedule-week-next"]');
    this.inviteCodeModal = page.locator('app-invite-code-modal');
  }

  async goto() {
    await this.page.goto('/coach');
    await this.page.waitForLoadState('networkidle');
  }

  getAthleteRows() {
    return this.page.locator('[data-testid="athlete-row"]');
  }

  getAthleteRow(name: string) {
    return this.page.locator('[data-testid="athlete-row"]').filter({ hasText: name });
  }

  async selectAthlete(name: string) {
    await this.getAthleteRow(name).click();
    await this.page.waitForTimeout(300);
  }

  getTagFilterChip(tagName: string) {
    return this.page.locator('[data-testid="tag-filter-chip"]').filter({ hasText: tagName });
  }

  async openAssignModal() {
    await this.page.locator('[data-testid="assign-btn"]').click();
  }

  getScheduleRows() {
    return this.page.locator('[data-testid="schedule-row"]');
  }

  getScheduleRowByTitle(title: string) {
    return this.page.locator('[data-testid="schedule-row"]').filter({ hasText: title });
  }

  async openInviteCodeModal() {
    await this.page.locator('[data-testid="invite-code-btn"]').click();
  }

  getGroupChips() {
    return this.page.locator('.chip.tag');
  }
}
