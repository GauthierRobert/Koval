import { test, expect } from '../fixtures/base.fixture';
import { ClubDetailPage } from '../page-objects/club-detail.page';
import {
  createClub,
  deleteClub,
  joinClub,
  getClubMembers,
  approveMember,
  createClubGroup,
  addMemberToClubGroup,
} from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Club - Members', () => {
  let clubDetailPage: ClubDetailPage;
  let clubId: string;

  test.beforeEach(async ({ page, coach, apiContext }) => {
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Members Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;
    clubDetailPage = new ClubDetailPage(page);
  });

  test.afterEach(async ({ apiContext, coach }) => {
    await deleteClub(apiContext, coach.token, clubId).catch(() => {});
  });

  test('view active members shows owner', async ({ page, coach, authenticatedCoachPage }) => {
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('members');

    // Owner should be listed
    const memberRow = clubDetailPage.getMemberRow(coach.displayName);
    await expect(memberRow).toBeVisible({ timeout: 10_000 });
  });

  test('approve pending request in private club', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Create private club instead
    await deleteClub(apiContext, coach.token, clubId).catch(() => {});
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Private Members Club',
      visibility: 'PRIVATE',
    });
    clubId = club.id;

    // Athlete requests to join
    await joinClub(apiContext, athlete.token, clubId);

    // Login as coach
    await injectAuth(page, coach);
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('members');

    // Pending member should be visible
    const pendingRow = clubDetailPage.getPendingMemberRow(athlete.displayName);
    await expect(pendingRow).toBeVisible({ timeout: 10_000 });

    // Approve
    await clubDetailPage.approveMember(athlete.displayName);

    // Pending row should disappear, member should appear in active list
    await expect(pendingRow).not.toBeVisible({ timeout: 5000 });
    const activeRow = clubDetailPage.getMemberRow(athlete.displayName);
    await expect(activeRow).toBeVisible();
  });

  test('change member role', async ({ page, coach, athlete, apiContext }) => {
    // Join as athlete
    await joinClub(apiContext, athlete.token, clubId);

    // Login as coach (owner)
    await injectAuth(page, coach);
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('members');

    // Wait for member to appear
    const memberRow = clubDetailPage.getMemberRow(athlete.displayName);
    await expect(memberRow).toBeVisible({ timeout: 10_000 });

    // Change role to COACH
    await clubDetailPage.changeMemberRole(athlete.displayName, 'COACH');
    await page.waitForTimeout(500);

    // Reload and verify
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('members');
    const roleSelect = clubDetailPage.getMemberRow(athlete.displayName).locator('.role-select');
    await expect(roleSelect).toHaveValue('COACH');
  });

  test('create and delete club group', async ({ page, coach, authenticatedCoachPage }) => {
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('members');

    // Create group
    await clubDetailPage.createGroup('Sprinters');

    // Verify group appears
    const groupCard = clubDetailPage.getGroupCard('Sprinters');
    await expect(groupCard).toBeVisible();

    // Delete group
    await clubDetailPage.deleteGroup('Sprinters');
    await expect(groupCard).not.toBeVisible({ timeout: 5000 });
  });

  test('add and remove member from group', async ({
    page,
    coach,
    athlete,
    apiContext,
  }) => {
    // Join as athlete
    await joinClub(apiContext, athlete.token, clubId);

    // Create group via API
    const group = await createClubGroup(apiContext, coach.token, clubId, 'Climbers');

    // Login as coach
    await injectAuth(page, coach);
    await clubDetailPage.goto(clubId);
    await clubDetailPage.switchTab('members');

    // Add athlete to group via dropdown
    const memberRow = clubDetailPage.getMemberRow(athlete.displayName);
    await expect(memberRow).toBeVisible({ timeout: 10_000 });
    await clubDetailPage.addMemberToTag(athlete.displayName, group.id);

    // Verify tag appears on member
    const tags = clubDetailPage.getMemberTags(athlete.displayName);
    await expect(tags.filter({ hasText: 'Climbers' })).toBeVisible();
  });
});
