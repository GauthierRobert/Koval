import { type Page, type Locator } from '@playwright/test';

export class ClubDetailPage {
  readonly page: Page;
  readonly backBtn: Locator;
  readonly joinBtn: Locator;
  readonly leaveBtn: Locator;
  readonly inviteCodeChip: Locator;
  readonly copyInviteBtn: Locator;

  constructor(page: Page) {
    this.page = page;
    this.backBtn = page.locator('[data-testid="back-btn"]');
    this.joinBtn = page.locator('[data-testid="btn-join-club"]');
    this.leaveBtn = page.locator('[data-testid="btn-leave-club"]');
    this.inviteCodeChip = page.locator('[data-testid="invite-code-chip"]');
    this.copyInviteBtn = page.locator('[data-testid="btn-copy-invite-code"]');
  }

  async goto(clubId: string) {
    await this.page.goto(`/clubs/${clubId}`);
    await this.page.waitForLoadState('networkidle');
  }

  // --- Tabs ---

  async switchTab(tabId: string) {
    await this.page.locator(`[data-testid="tab-${tabId}"]`).click();
    await this.page.waitForTimeout(300);
  }

  getHeaderTitle() {
    return this.page.locator('.header-title');
  }

  getHeaderBadges() {
    return this.page.locator('.h-badge');
  }

  getRoleBadge() {
    return this.page.locator('.h-badge--role');
  }

  // --- Members Tab ---

  getGroupCards() {
    return this.page.locator('.group-card');
  }

  getGroupCard(name: string) {
    return this.page.locator('.group-card').filter({ hasText: name });
  }

  getGroupCreateInput() {
    return this.page.locator('[data-testid="group-create-input"]');
  }

  getGroupCreateBtn() {
    return this.page.locator('[data-testid="btn-add-group"]');
  }

  async createGroup(name: string) {
    await this.getGroupCreateInput().fill(name);
    await this.getGroupCreateBtn().click();
    await this.page.waitForTimeout(300);
  }

  async deleteGroup(name: string) {
    await this.getGroupCard(name).locator('.tag-delete').click();
    await this.page.waitForTimeout(300);
  }

  getMemberRows() {
    return this.page.locator('[data-testid="member-row"]');
  }

  getMemberRow(name: string) {
    return this.page.locator('[data-testid="member-row"]').filter({ hasText: name });
  }

  getPendingMemberRows() {
    return this.page.locator('[data-testid="pending-member-row"]');
  }

  getPendingMemberRow(name: string) {
    return this.page.locator('[data-testid="pending-member-row"]').filter({ hasText: name });
  }

  async approveMember(name: string) {
    await this.getPendingMemberRow(name).locator('.btn-approve').click();
    await this.page.waitForTimeout(300);
  }

  async rejectMember(name: string) {
    await this.getPendingMemberRow(name).locator('.btn-reject').click();
    await this.page.waitForTimeout(300);
  }

  async changeMemberRole(name: string, role: string) {
    await this.getMemberRow(name).locator('.role-select').selectOption(role);
    await this.page.waitForTimeout(300);
  }

  getMemberRoleBadge(name: string) {
    return this.getMemberRow(name).locator('.role-badge, .role-select');
  }

  async addMemberToTag(memberName: string, tagId: string) {
    await this.getMemberRow(memberName).locator('.tag-select').selectOption(tagId);
    await this.page.waitForTimeout(300);
  }

  getMemberTags(memberName: string) {
    return this.getMemberRow(memberName).locator('.club-tag');
  }

  // --- Sessions Tab ---

  getSessionCards() {
    return this.page.locator('app-session-card');
  }

  getSessionCard(title: string) {
    return this.page.locator('app-session-card').filter({ hasText: title });
  }

  getAddSessionBtn() {
    return this.page.locator('[data-testid="btn-add-session"]');
  }

  // Session form
  getSessionFormModal() {
    return this.page.locator('[data-testid="session-form-modal"]');
  }

  getSessionFormTitle() {
    return this.page.locator('[data-testid="form-session-title"]');
  }

  getSessionFormSport() {
    return this.page.locator('[data-testid="form-session-sport"]');
  }

  getSessionFormDayOfWeek() {
    return this.page.locator('[data-testid="form-day-of-week"]');
  }

  getSessionFormTimeOfDay() {
    return this.page.locator('[data-testid="form-time-of-day"]');
  }

  getSessionFormMaxParticipants() {
    return this.page.locator('[data-testid="form-max-participants"]');
  }

  getSessionFormDuration() {
    return this.page.locator('[data-testid="form-duration-minutes"]');
  }

  getSessionFormLocation() {
    return this.page.locator('[data-testid="form-session-location"]');
  }

  getSessionFormDescription() {
    return this.page.locator('[data-testid="form-session-description"]');
  }

  getSessionFormSubmitBtn() {
    return this.page.locator('[data-testid="btn-create-session"]');
  }

  getSessionFormCancelBtn() {
    return this.page.locator('[data-testid="btn-cancel-form"]');
  }

  async createRecurringSession(data: {
    title: string;
    sport?: string;
    dayOfWeek: string;
    timeOfDay: string;
    location?: string;
    description?: string;
  }) {
    await this.getAddSessionBtn().click();
    await this.getSessionFormTitle().fill(data.title);
    if (data.sport) {
      await this.getSessionFormSport().selectOption(data.sport);
    }
    await this.getSessionFormDayOfWeek().selectOption(data.dayOfWeek);
    await this.getSessionFormTimeOfDay().fill(data.timeOfDay);
    if (data.location) {
      await this.getSessionFormLocation().fill(data.location);
    }
    if (data.description) {
      await this.getSessionFormDescription().fill(data.description);
    }
    await this.getSessionFormSubmitBtn().click();
    await this.page.waitForTimeout(500);
  }

  // Session actions
  async joinSession(title: string) {
    const card = this.getSessionCard(title);
    await card.locator('.btn-primary.btn-sm').click();
    await this.page.waitForTimeout(300);
  }

  async leaveSession(title: string) {
    const card = this.getSessionCard(title);
    await card.locator('.btn-ghost.btn-sm').click();
    await this.page.waitForTimeout(300);
  }

  getSessionAttendingBadge(title: string) {
    return this.getSessionCard(title).locator('.sc-status-pill--attending');
  }

  getSessionWaitingBadge(title: string) {
    return this.getSessionCard(title).locator('.sc-status-pill--waiting');
  }

  getLinkedTraining(sessionTitle: string) {
    return this.getSessionCard(sessionTitle).locator('.sc-linked');
  }

  // --- Recurring session dialogs ---

  getRecurringEditDialog() {
    return this.page.locator('[data-testid="recurring-edit-dialog"]');
  }

  getEditThisOnlyBtn() {
    return this.page.locator('[data-testid="btn-edit-this-only"]');
  }

  getEditAllFutureBtn() {
    return this.page.locator('[data-testid="btn-edit-all-future"]');
  }

  getCancelRecurringDialog() {
    return this.page.locator('[data-testid="recurring-cancel-dialog"]');
  }

  getCancelThisOnlyBtn() {
    return this.page.locator('[data-testid="btn-cancel-this-only"]');
  }

  getCancelAllFutureBtn() {
    return this.page.locator('[data-testid="btn-cancel-all-future"]');
  }

  getCancelConfirmModal() {
    return this.page.locator('[data-testid="cancel-confirm-modal"]');
  }

  getCancelReasonTextarea() {
    return this.page.locator('[data-testid="cancel-reason-textarea"]');
  }

  getConfirmCancelBtn() {
    return this.page.locator('[data-testid="btn-confirm-cancel"]');
  }

  // Navigation
  getSessionsNavPrev() {
    return this.page.locator('[data-testid="sessions-nav-prev"]');
  }

  getSessionsNavNext() {
    return this.page.locator('[data-testid="sessions-nav-next"]');
  }

  getSessionsNavToday() {
    return this.page.locator('[data-testid="sessions-nav-today"]');
  }

  // Open Sessions tab
  getProposeSessionBtn() {
    return this.page.locator('[data-testid="btn-propose-session"]');
  }

}
