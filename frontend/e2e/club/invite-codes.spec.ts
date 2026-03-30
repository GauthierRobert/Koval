import { test, expect } from '../fixtures/base.fixture';
import { ClubDetailPage } from '../page-objects/club-detail.page';
import {
  createClub,
  deleteClub,
  createClubInviteCode,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Club - Invite Codes', () => {
  let clubDetailPage: ClubDetailPage;
  let clubId: string;

  test.beforeEach(async ({ page, coach, apiContext }) => {
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Invite Club',
      visibility: 'PRIVATE',
    });
    clubId = club.id;
    clubDetailPage = new ClubDetailPage(page);
  });

  test.afterEach(async ({ apiContext, coach }) => {
    await deleteClub(apiContext, coach.token, clubId).catch(() => {});
  });

  test('generate club invite code', async ({ page, coach, apiContext, authenticatedCoachPage }) => {
    // Generate via API
    const inviteCode = await createClubInviteCode(apiContext, coach.token, clubId);
    expect(inviteCode.code).toBeTruthy();
    expect(inviteCode.code.length).toBe(8);

    // Navigate to club detail and verify invite code is visible
    await clubDetailPage.goto(clubId);
    await expect(clubDetailPage.inviteCodeChip).toBeVisible({ timeout: 10_000 });
  });

  test('redeem club invite code and join', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Generate invite code
    const inviteCode = await createClubInviteCode(apiContext, coach.token, clubId);

    // Redeem as athlete via API (redeem-invite endpoint)
    const response = await apiContext.post('http://localhost:8080/api/clubs/redeem-invite', {
      headers: { Authorization: `Bearer ${athlete.token}` },
      data: { code: inviteCode.code },
    });
    expect(response.ok()).toBeTruthy();

    // Verify athlete is now a member
    const membersResponse = await apiContext.get(
      `http://localhost:8080/api/clubs/${clubId}/members`,
      { headers: { Authorization: `Bearer ${coach.token}` } }
    );
    const members = await membersResponse.json();
    expect(members.some((m: any) => m.userId === athlete.id)).toBeTruthy();

    // Login as athlete and verify club appears in their list
    await injectAuth(page, athlete);
    await page.goto('/clubs');
    await page.waitForLoadState('networkidle');

    const clubCard = page.locator('[data-testid="club-card"]').filter({ hasText: 'E2E Invite Club' });
    await expect(clubCard).toBeVisible({ timeout: 10_000 });
  });

  test('invite code with group auto-join', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Create a club group
    const groupResponse = await apiContext.post(
      `http://localhost:8080/api/clubs/${clubId}/groups`,
      {
        headers: { Authorization: `Bearer ${coach.token}` },
        data: { name: 'E2E Invite Group' },
      }
    );
    const group = await groupResponse.json();

    // Generate invite code with group
    const inviteCode = await createClubInviteCode(apiContext, coach.token, clubId, {
      clubGroupId: group.id,
    });

    // Redeem as athlete
    const response = await apiContext.post('http://localhost:8080/api/clubs/redeem-invite', {
      headers: { Authorization: `Bearer ${athlete.token}` },
      data: { code: inviteCode.code },
    });
    expect(response.ok()).toBeTruthy();

    // Verify athlete is in the group
    const groupsResponse = await apiContext.get(
      `http://localhost:8080/api/clubs/${clubId}/groups`,
      { headers: { Authorization: `Bearer ${coach.token}` } }
    );
    const groups = await groupsResponse.json();
    const targetGroup = groups.find((g: any) => g.name === 'E2E Invite Group');
    expect(targetGroup.memberIds).toContain(athlete.id);
  });
});
