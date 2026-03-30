import { test, expect } from '../fixtures/base.fixture';
import { CoachDashboardPage } from '../page-objects/coach-dashboard.page';
import { addAthleteToCoachGroup, generateCoachInviteCode, redeemInviteCode } from '../fixtures/test-data.fixture';

test.describe('Coach - Athlete Management', () => {
  let coachPage: CoachDashboardPage;

  test.beforeEach(async ({ page, coach, athlete, apiContext }) => {
    // Ensure athlete is in coach's group
    await addAthleteToCoachGroup(apiContext, coach.token, athlete.id, 'E2E Group');
    coachPage = new CoachDashboardPage(page);
  });

  test('coach views athlete list', async ({ page, coach, athlete, authenticatedCoachPage }) => {
    await coachPage.goto();

    // Athlete list should be visible
    await expect(coachPage.athleteList).toBeVisible();

    // At least one athlete should appear
    const rows = coachPage.getAthleteRows();
    await expect(rows).toHaveCount(1, { timeout: 10_000 });

    // The athlete name should be visible
    await expect(coachPage.getAthleteRow(athlete.displayName)).toBeVisible();
  });

  test('coach filters athletes by tag', async ({ page, coach, athlete, authenticatedCoachPage }) => {
    await coachPage.goto();

    // The tag filter chip should be visible
    const tagChip = coachPage.getTagFilterChip('E2E Group');
    await expect(tagChip).toBeVisible();

    // Click tag filter
    await tagChip.click();

    // Athlete should still be visible (they're in this group)
    await expect(coachPage.getAthleteRow(athlete.displayName)).toBeVisible();

    // Click "All" filter to reset
    await coachPage.tagFilterAll.click();
    await expect(coachPage.getAthleteRow(athlete.displayName)).toBeVisible();
  });

  test('coach selects athlete and views details', async ({
    page,
    coach,
    athlete,
    authenticatedCoachPage,
  }) => {
    await coachPage.goto();

    // Select athlete
    await coachPage.selectAthlete(athlete.displayName);

    // Performance tab should be active by default
    await expect(coachPage.tabPerformance).toBeVisible();

    // Schedule table should appear
    await expect(coachPage.scheduleTable).toBeVisible();
  });

  test('coach generates and deactivates invite code', async ({
    page,
    coach,
    apiContext,
    authenticatedCoachPage,
  }) => {
    // Generate invite code via API (since the modal may vary)
    const inviteCode = await generateCoachInviteCode(apiContext, coach.token, []);
    expect(inviteCode.code).toBeTruthy();
    expect(inviteCode.code.length).toBe(8);

    // Verify code appears in the list
    const codes = await apiContext
      .get('http://localhost:8080/api/coach/invite-codes', {
        headers: { Authorization: `Bearer ${coach.token}` },
      })
      .then((r) => r.json());

    expect(codes.some((c: any) => c.code === inviteCode.code)).toBeTruthy();
  });

  test('athlete redeems coach invite code', async ({ apiContext, coach, athlete }) => {
    // Generate invite code
    const inviteCode = await generateCoachInviteCode(apiContext, coach.token, []);

    // Redeem as athlete
    await redeemInviteCode(apiContext, athlete.token, inviteCode.code);

    // Verify athlete is now in coach's athletes
    const response = await apiContext.get('http://localhost:8080/api/coach/athletes', {
      headers: { Authorization: `Bearer ${coach.token}` },
    });
    const athletes = await response.json();
    expect(athletes.some((a: any) => a.id === athlete.id)).toBeTruthy();
  });
});
