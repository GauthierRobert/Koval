import { test, expect } from '../fixtures/base.fixture';
import { ClubsListPage } from '../page-objects/clubs-list.page';
import { createClub, deleteClub, joinClub, leaveClub } from '../fixtures/test-data.fixture';
import { injectAuth } from '../fixtures/auth.fixture';

test.describe('Club - Management', () => {
  let clubsListPage: ClubsListPage;
  const cleanupClubIds: string[] = [];

  test.afterEach(async ({ apiContext, coach }) => {
    for (const id of cleanupClubIds) {
      await deleteClub(apiContext, coach.token, id).catch(() => {});
    }
    cleanupClubIds.length = 0;
  });

  test('create a public club', async ({ page, coach, apiContext, authenticatedCoachPage }) => {
    clubsListPage = new ClubsListPage(page);
    await clubsListPage.goto();

    // Click create
    await clubsListPage.createClubBtn.click();
    await expect(clubsListPage.modal).toBeVisible();

    // Fill form
    await clubsListPage.nameInput.fill('E2E Test Club');
    await clubsListPage.descriptionInput.fill('Created by E2E test');
    await clubsListPage.visibilitySelect.selectOption('PUBLIC');

    // Submit
    await clubsListPage.submitBtn.click();
    await page.waitForTimeout(1000);

    // Verify club appears in "Your Clubs"
    const clubCard = clubsListPage.getClubCard('E2E Test Club');
    await expect(clubCard).toBeVisible({ timeout: 10_000 });

    // Track for cleanup via API
    const response = await apiContext.get('http://localhost:8080/api/clubs', {
      headers: { Authorization: `Bearer ${coach.token}` },
    });
    const clubs = await response.json();
    const created = clubs.find((c: any) => c.name === 'E2E Test Club');
    if (created) cleanupClubIds.push(created.id);
  });

  test('create a private club', async ({ page, coach, apiContext, authenticatedCoachPage }) => {
    clubsListPage = new ClubsListPage(page);
    await clubsListPage.goto();

    await clubsListPage.createClub('E2E Private Club', 'Private test club', 'PRIVATE');

    const clubCard = clubsListPage.getClubCard('E2E Private Club');
    await expect(clubCard).toBeVisible({ timeout: 10_000 });

    // Verify it shows as PRIVATE
    await expect(clubCard.locator('.meta-chip--private')).toBeVisible();

    // Cleanup
    const response = await apiContext.get('http://localhost:8080/api/clubs', {
      headers: { Authorization: `Bearer ${coach.token}` },
    });
    const clubs = await response.json();
    const created = clubs.find((c: any) => c.name === 'E2E Private Club');
    if (created) cleanupClubIds.push(created.id);
  });

  test('browse and join public club', async ({ page, coach, athlete, apiContext }) => {
    // Create club as coach
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Join Club',
      visibility: 'PUBLIC',
    });
    cleanupClubIds.push(club.id);

    // Login as athlete
    await injectAuth(page, athlete);
    clubsListPage = new ClubsListPage(page);
    await clubsListPage.goto();

    // Find and join the public club
    const joinBtn = clubsListPage.getJoinBtn('E2E Join Club');
    await expect(joinBtn).toBeVisible({ timeout: 10_000 });
    await joinBtn.click();
    await page.waitForTimeout(1000);

    // Reload and verify membership
    await clubsListPage.goto();
    const clubCard = clubsListPage.getClubCard('E2E Join Club');
    await expect(clubCard).toBeVisible();
  });

  test('leave a club', async ({ page, coach, athlete, apiContext }) => {
    // Create club and join as athlete
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Leave Club',
      visibility: 'PUBLIC',
    });
    cleanupClubIds.push(club.id);
    await joinClub(apiContext, athlete.token, club.id);

    // Login as athlete
    await injectAuth(page, athlete);
    clubsListPage = new ClubsListPage(page);
    await clubsListPage.goto();

    // Verify club is in "Your Clubs"
    const clubCard = clubsListPage.getClubCard('E2E Leave Club');
    await expect(clubCard).toBeVisible();

    // Leave
    const leaveBtn = clubsListPage.getLeaveBtn('E2E Leave Club');
    await leaveBtn.click();
    await page.waitForTimeout(1000);

    // Reload and verify club is no longer in "Your Clubs"
    await clubsListPage.goto();
    await expect(clubsListPage.getClubCard('E2E Leave Club').first()).not.toBeVisible({
      timeout: 5000,
    });
  });
});
